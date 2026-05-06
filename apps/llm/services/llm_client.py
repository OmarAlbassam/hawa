import asyncio
import json
import logging
import random
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

import httpx
from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AsyncOpenAI,
    InternalServerError,
    RateLimitError,
)
from pydantic import ValidationError

from config import (
    GROQ_BEST_EFFORT_SCHEMA_MODELS,
    GROQ_STRICT_SCHEMA_MODELS,
    Provider,
    Settings,
)
from models import SentimentResponse
from services.rate_limiter import ProviderRateLimiter

logger = logging.getLogger(__name__)


class RateLimitExhaustedError(Exception):
    """Raised when retry budget for a rate-limited request is exhausted."""

    def __init__(self, attempts: int, last_retry_after: float) -> None:
        super().__init__(
            f"rate-limited after {attempts} attempts "
            f"(last Retry-After={last_retry_after:.1f}s)"
        )
        self.attempts = attempts
        self.last_retry_after = last_retry_after


@dataclass(frozen=True)
class TokenUsage:
    """Server-reported token counts for a single LLM call."""

    prompt_tokens: int
    completion_tokens: int
    total_tokens: int
    cached_tokens: int | None = None

    @classmethod
    def from_completion(cls, completion: Any) -> "TokenUsage | None":
        """Extract usage from an OpenAI ChatCompletion response.

        Some self-hosted backends omit `.usage`; callers must tolerate None.

        ``cached_tokens`` is read from ``usage.prompt_tokens_details.cached_tokens``
        — the OpenAI-standard surface that Fireworks/Groq/OpenAI all use to report
        prefix-cache hits. Backends that don't expose it leave the field None.
        """
        usage = getattr(completion, "usage", None)
        if usage is None:
            return None
        try:
            return cls(
                prompt_tokens=int(usage.prompt_tokens),
                completion_tokens=int(usage.completion_tokens),
                total_tokens=int(usage.total_tokens),
                cached_tokens=_extract_cached_tokens(usage),
            )
        except (AttributeError, TypeError, ValueError):
            return None


def _extract_cached_tokens(usage: Any) -> int | None:
    details = getattr(usage, "prompt_tokens_details", None)
    if details is None:
        return None
    if isinstance(details, dict):
        cached = details.get("cached_tokens")
    else:
        cached = getattr(details, "cached_tokens", None)
    # Strict type check on purpose: MagicMock attribute access returns child
    # mocks that satisfy duck-typed conversions, which would silently inject
    # fabricated counts in tests. Only accept real numbers from real responses.
    if not isinstance(cached, (int, float)) or isinstance(cached, bool):
        return None
    return int(cached)


class _Outcome(StrEnum):
    RATE_LIMIT = "rate_limit"
    TRANSIENT = "transient"
    FATAL_CONFIG = "fatal_config"
    FATAL_VALIDATION = "fatal_validation"
    UNKNOWN = "unknown"


class LLMClient:
    def __init__(
        self, settings: Settings, rate_limiter: ProviderRateLimiter
    ) -> None:
        # Size the httpx pool to the configured concurrency. The default
        # max_keepalive_connections=20 silently caps in-flight requests on
        # RunPod where max_concurrency defaults to 32 — unrelated to our
        # semaphore but observable as serialization on the wire.
        limits = httpx.Limits(
            max_connections=max(settings.max_concurrency * 2, 20),
            max_keepalive_connections=max(settings.max_concurrency, 20),
        )
        self._http_client = httpx.AsyncClient(
            limits=limits,
            timeout=httpx.Timeout(
                settings.request_timeout_s,
                connect=settings.connect_timeout_s,
            ),
        )
        # max_retries=0 disables the SDK's internal exponential-backoff retry
        # loop. We own retry policy here so it doesn't stack with ours.
        self.client = AsyncOpenAI(
            base_url=settings.base_url,
            api_key=settings.api_key,
            max_retries=0,
            http_client=self._http_client,
        )
        self.model = settings.model
        self.temperature = settings.temperature
        self.max_tokens = settings.max_tokens
        self.rate_limiter = rate_limiter
        self.max_retries = settings.rate_max_retries
        self.initial_backoff = settings.rate_initial_backoff_s
        self.max_backoff = settings.rate_max_backoff_s
        self.min_pause = settings.rate_min_pause_s
        self.pause_padding = settings.rate_pause_padding
        self.extra_body = _build_extra_body(settings)
        self.response_format = _build_response_format(settings.provider, self.model)

    async def aclose(self) -> None:
        """Close the httpx client we own.

        The OpenAI SDK doesn't manage the lifecycle of a user-supplied
        ``http_client``, so we close it explicitly from the FastAPI lifespan.
        """
        await self._http_client.aclose()

    async def analyze(
        self,
        system_prompt: str,
        text: str,
        *,
        session_id: str | None = None,
    ) -> SentimentResponse:
        """Send text to LLM and return a validated SentimentResponse.

        Wraps `_analyze_inner` and discards the raw completion. Production
        callers (analyzer.py) use this; the benchmark uses
        `analyze_with_usage` to also capture server-reported token counts.

        ``session_id``, when set, is forwarded as the ``x-session-affinity``
        header so providers that prefix-cache (Fireworks, Groq) route requests
        sharing a system prompt to the same replica.
        """
        response, _ = await self._analyze_inner(
            system_prompt, text, session_id=session_id
        )
        return response

    async def analyze_with_usage(
        self,
        system_prompt: str,
        text: str,
        *,
        session_id: str | None = None,
    ) -> tuple[SentimentResponse, TokenUsage | None]:
        """Same as `analyze`, but also returns the server's token counts.

        Usage may be None if the provider doesn't return a `.usage` field
        (e.g. some self-hosted Ollama setups) — callers must tolerate that.
        """
        response, completion = await self._analyze_inner(
            system_prompt, text, session_id=session_id
        )
        return response, TokenUsage.from_completion(completion)

    async def _analyze_inner(
        self,
        system_prompt: str,
        text: str,
        *,
        session_id: str | None = None,
    ) -> tuple[SentimentResponse, Any]:
        """Shared retry loop for `analyze` and `analyze_with_usage`.

        Returns the parsed `SentimentResponse` plus the raw OpenAI
        `ChatCompletion` object (which carries `.usage` for token accounting).
        Failure classification:

        - 429 → notify the shared pause gate with Retry-After, retry.
        - Transient (5xx / timeout / connection) → local backoff, retry.
          Does not trip the pause gate — one bad response from any provider
          shouldn't stall every concurrent worker.
        - 4xx config errors and JSON-validation failures → propagate
          immediately; retrying won't change the outcome.
        """
        estimated_tokens = _estimate_tokens(system_prompt, text, self.max_tokens)
        last_retry_after = 0.0
        last_outcome = _Outcome.UNKNOWN
        last_transient: Exception | None = None

        for attempt in range(1, self.max_retries + 1):
            await self.rate_limiter.acquire(estimated_tokens)
            try:
                kwargs: dict[str, Any] = {
                    "model": self.model,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": text},
                    ],
                    "temperature": self.temperature,
                    "max_tokens": self.max_tokens,
                    "response_format": self.response_format,
                }
                if self.extra_body is not None:
                    kwargs["extra_body"] = self.extra_body
                if session_id is not None:
                    # Fireworks (and Groq) honor x-session-affinity to pin
                    # requests sharing a prefix to the same replica, which is
                    # how their automatic prompt caching pays off across a
                    # batch. Backends that don't recognize the header ignore it.
                    kwargs["extra_headers"] = {"x-session-affinity": session_id}
                completion = await self.client.chat.completions.create(**kwargs)
                content = completion.choices[0].message.content or ""
                response = SentimentResponse.model_validate_json(content)
                return response, completion
            except Exception as exc:
                outcome = _classify(exc)
                last_outcome = outcome

                if outcome is _Outcome.RATE_LIMIT:
                    retry_after = _parse_retry_after(
                        exc,
                        fallback=_backoff(
                            attempt, self.initial_backoff, self.max_backoff
                        ),
                    )
                    pause = max(self.min_pause, retry_after * self.pause_padding)
                    last_retry_after = pause
                    self.rate_limiter.notify_rate_limited(pause)
                    continue

                if outcome is _Outcome.TRANSIENT:
                    last_transient = exc
                    await asyncio.sleep(
                        _backoff(attempt, self.initial_backoff, self.max_backoff)
                    )
                    continue

                # FATAL_CONFIG, FATAL_VALIDATION, UNKNOWN — no retry.
                raise

        # Retry budget exhausted. Signal which flavour ran out:
        # - rate-limit exhaustion gets its dedicated error so the analyzer can
        #   tag results with a distinct "rate_limited:" prefix.
        # - transient exhaustion re-raises the last underlying error so the
        #   analyzer's existing APIConnectionError → "LLM unreachable" path
        #   (and any future provider-specific handling) keeps working.
        if last_outcome is _Outcome.RATE_LIMIT:
            raise RateLimitExhaustedError(self.max_retries, last_retry_after)
        if last_transient is not None:
            raise last_transient
        # Defensive: loop shouldn't complete without setting one of the above.
        raise RuntimeError("analyze() exhausted retries without a recorded failure")

    async def is_reachable(self) -> bool:
        """Check if the LLM endpoint is reachable."""
        try:
            await self.client.models.list()
            return True
        except Exception:
            return False


def _estimate_tokens(system_prompt: str, text: str, max_output: int) -> int:
    """Rough estimator: ~4 chars per token, plus the max output budget."""
    chars = len(system_prompt) + len(text)
    return (chars // 4) + max_output


# Computed once at import: vLLM's guided-decoding backends parse this JSON
# schema and constrain token generation to outputs that satisfy it. The schema
# resolves Emotion / IrrelevanceReason via $defs $refs, which outlines and
# xgrammar both handle natively.
_SENTIMENT_SCHEMA = SentimentResponse.model_json_schema()


def _build_extra_body(settings: Settings) -> dict[str, Any] | None:
    """Per-(provider, model, settings) `extra_body` for chat.completions; None when none applies.

    Combines provider-specific quirks with settings-driven knobs:

    - **Qwen3 thinking.** Qwen3 emits a ``<think>...</think>`` block by
      default, breaking JSON-mode parsing and wasting tokens for our
      structured-output use case. vLLM exposes a hard switch via
      ``chat_template_kwargs.enable_thinking``; Ollama's OpenAI-compatible
      endpoint passes the field through to the underlying chat template.
      Groq does NOT accept this property and rejects the request with
      400 ``property 'chat_template_kwargs' is unsupported`` — so we only
      emit it for the backends that actually honor it.

    - **vLLM guided JSON (RunPod).** ``response_format={"type":
      "json_object"}`` only asks for syntactically-valid JSON. vLLM additionally
      supports ``guided_json``, which constrains decoding to a JSON Schema at
      the token level. With it, schema mismatches and out-of-enum values become
      structurally impossible — eliminating an entire class of FATAL_VALIDATION
      failures that retrying can't fix.

    - **Ollama format schema.** Ollama 0.5+ accepts a ``format`` field carrying
      a JSON Schema and constrains generation to it (the same guarantee vLLM
      gives via ``guided_json``). Its OpenAI-compatible endpoint passes
      ``extra_body`` through to the underlying call, so attaching it here is
      enough.

    - **Groq qwen3 reasoning_effort.** Groq doesn't honor
      ``chat_template_kwargs``; instead it exposes ``reasoning_effort`` for
      reasoning-capable models. ``"none"`` disables the ``<think>`` block so
      the entire ``max_tokens`` budget goes to the JSON response.

    - **Settings reasoning_effort.** When ``LLM_REASONING_EFFORT`` is set
      (gpt-oss / o-series on any provider), forward it as a top-level body
      field. Overrides the qwen3-on-groq default above for explicit user intent.
    """
    provider = settings.provider
    model = settings.model
    body: dict[str, Any] = {}
    if "qwen3" in model.lower() and provider != Provider.GROQ:
        body["chat_template_kwargs"] = {"enable_thinking": False}
    if provider == Provider.GROQ and "qwen3" in model.lower():
        body["reasoning_effort"] = "none"
    if provider == Provider.RUNPOD:
        body["guided_json"] = _SENTIMENT_SCHEMA
    elif provider == Provider.OLLAMA:
        body["format"] = _SENTIMENT_SCHEMA
    if settings.reasoning_effort is not None:
        body["reasoning_effort"] = settings.reasoning_effort
    return body or None


def _build_response_format(provider: Provider, model: str) -> dict[str, Any]:
    """Per-(provider, model) `response_format` for chat.completions.

    Returns the strongest constraint the backend supports:

    - **Groq strict** (``GROQ_STRICT_SCHEMA_MODELS``) → ``json_schema`` with
      ``strict: True``. Constrained decoding, true 100% guarantee.
    - **Groq best-effort** (``GROQ_BEST_EFFORT_SCHEMA_MODELS``) → ``json_schema``
      with ``strict: False``. Stronger nudge than ``json_object``; the coercion
      layer in ``models.py`` is the safety net for the gap.
    - **Fireworks** → ``json_schema`` with ``strict: True``. Fireworks
      enforces the schema via xgrammar/outlines at decode time, so out-of-enum
      tokens (e.g. Llama emitting ``"FRUSTRATION"``) become structurally
      impossible. Docs: https://docs.fireworks.ai/structured-responses/structured-output-grammar-based.
    - **Everything else** → ``json_object``. Validates JSON shape only; the
      coercion layer carries the enum guarantee. RunPod (vLLM) and Ollama
      enforce the schema via ``extra_body`` instead, so plain ``json_object``
      here is correct for them.
    """
    if provider == Provider.GROQ:
        if model in GROQ_STRICT_SCHEMA_MODELS:
            return {
                "type": "json_schema",
                "json_schema": {
                    "name": "sentiment_response",
                    "schema": _strict_schema(_SENTIMENT_SCHEMA),
                    "strict": True,
                },
            }
        if model in GROQ_BEST_EFFORT_SCHEMA_MODELS:
            return {
                "type": "json_schema",
                "json_schema": {
                    "name": "sentiment_response",
                    "schema": _SENTIMENT_SCHEMA,
                    "strict": False,
                },
            }
    if provider == Provider.FIREWORKS:
        return {
            "type": "json_schema",
            "json_schema": {
                "name": "sentiment_response",
                "schema": _strict_schema(_SENTIMENT_SCHEMA),
                "strict": True,
            },
        }
    return {"type": "json_object"}


def _strict_schema(schema: dict[str, Any]) -> dict[str, Any]:
    """Make a Pydantic-emitted schema satisfy strict-mode rules.

    Strict mode (Groq, OpenAI) requires every property to appear in
    ``required`` and every object to declare ``additionalProperties: false``.
    Pydantic's default schema doesn't — fields with defaults are absent from
    ``required``, and ``additionalProperties`` is unset. Walks the schema
    tree (including ``$defs``) and patches both rules in place on a copy.
    """
    import copy

    out = copy.deepcopy(schema)

    def _patch(node: object) -> None:
        if isinstance(node, dict):
            if node.get("type") == "object" and "properties" in node:
                node["additionalProperties"] = False
                node["required"] = list(node["properties"].keys())
            for v in node.values():
                _patch(v)
        elif isinstance(node, list):
            for item in node:
                _patch(item)

    _patch(out)
    return out


_TRANSIENT_SDK_TYPES = (APIConnectionError, APITimeoutError, InternalServerError)


def _classify(exc: Exception) -> _Outcome:
    """Classify an outbound-call failure."""
    if isinstance(exc, RateLimitError):
        return _Outcome.RATE_LIMIT
    if isinstance(exc, _TRANSIENT_SDK_TYPES):
        return _Outcome.TRANSIENT
    if isinstance(exc, APIStatusError):
        status = _status_code(exc)
        if status == 429:
            return _Outcome.RATE_LIMIT
        if status is not None and status >= 500:
            return _Outcome.TRANSIENT
        # 4xx (or unreadable status) — no retry will fix it.
        return _Outcome.FATAL_CONFIG
    if isinstance(exc, (ValidationError, json.JSONDecodeError)):
        # Schema mismatch or malformed JSON. Retrying at the HTTP level won't
        # flip a prose-returning model into a JSON-returning one — propagate.
        return _Outcome.FATAL_VALIDATION
    return _Outcome.UNKNOWN


def _status_code(exc: Exception) -> int | None:
    status = getattr(exc, "status_code", None)
    if status is None:
        response = getattr(exc, "response", None)
        status = getattr(response, "status_code", None)
    return status


def _parse_retry_after(exc: Exception, fallback: float) -> float:
    response = getattr(exc, "response", None)
    headers = getattr(response, "headers", None)
    if headers is None:
        return fallback
    raw = headers.get("retry-after") or headers.get("Retry-After")
    if raw is None:
        return fallback
    try:
        return max(0.0, float(raw))
    except (TypeError, ValueError):
        return fallback


def _backoff(attempt: int, initial: float, maximum: float) -> float:
    # Jitter prevents N concurrent workers from computing identical sleeps and
    # slamming the upstream in lockstep on resume. Matters most for transient
    # 5xx during RunPod worker recycling, where the 429 pause-gate doesn't fire.
    base = min(maximum, initial * (2 ** (attempt - 1)))
    return base * random.uniform(0.5, 1.5)
