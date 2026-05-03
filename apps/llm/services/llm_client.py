import asyncio
import logging
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

import instructor
from instructor.core.exceptions import InstructorRetryException
from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AsyncOpenAI,
    InternalServerError,
    RateLimitError,
)

from config import Settings
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

    @classmethod
    def from_completion(cls, completion: Any) -> "TokenUsage | None":
        """Extract usage from an OpenAI ChatCompletion response.

        Self-hosted backends or instructor versions that don't surface the raw
        completion may return None here; callers must tolerate that.
        """
        usage = getattr(completion, "usage", None)
        if usage is None:
            return None
        try:
            return cls(
                prompt_tokens=int(usage.prompt_tokens),
                completion_tokens=int(usage.completion_tokens),
                total_tokens=int(usage.total_tokens),
            )
        except (AttributeError, TypeError, ValueError):
            return None


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
        # max_retries=0 disables the SDK's internal exponential-backoff retry
        # loop. We own retry policy here so it doesn't stack with ours.
        self._raw_client = AsyncOpenAI(
            base_url=settings.base_url,
            api_key=settings.api_key,
            max_retries=0,
        )
        self.client = instructor.from_openai(
            self._raw_client, mode=instructor.Mode.JSON
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

    async def analyze(self, system_prompt: str, text: str) -> SentimentResponse:
        """Send text to LLM and return a validated SentimentResponse.

        Wraps `_analyze_inner` and discards the raw completion. Production
        callers (analyzer.py) use this; the benchmark uses
        `analyze_with_usage` to also capture server-reported token counts.
        """
        response, _ = await self._analyze_inner(system_prompt, text)
        return response

    async def analyze_with_usage(
        self, system_prompt: str, text: str
    ) -> tuple[SentimentResponse, TokenUsage | None]:
        """Same as `analyze`, but also returns the server's token counts.

        Usage may be None if the provider doesn't return a `.usage` field
        (e.g. some self-hosted Ollama setups) — callers must tolerate that.
        """
        response, completion = await self._analyze_inner(system_prompt, text)
        return response, TokenUsage.from_completion(completion)

    async def _analyze_inner(
        self, system_prompt: str, text: str
    ) -> tuple[SentimentResponse, Any]:
        """Shared retry loop for `analyze` and `analyze_with_usage`.

        Returns the parsed `SentimentResponse` plus the raw OpenAI
        `ChatCompletion` object (or None when instructor doesn't surface it).
        Failure classification matches the original `analyze`:

        - 429 → notify the shared pause gate with Retry-After, retry.
        - Transient (5xx / timeout / connection) → local backoff, retry.
          Does not trip the pause gate — one bad response from any provider
          shouldn't stall every concurrent worker.
        - 4xx config errors and pure JSON-validation failures → propagate
          immediately; retrying won't change the outcome.
        """
        estimated_tokens = _estimate_tokens(system_prompt, text, self.max_tokens)
        last_retry_after = 0.0
        last_outcome = _Outcome.UNKNOWN
        last_transient: Exception | None = None

        for attempt in range(1, self.max_retries + 1):
            await self.rate_limiter.acquire(estimated_tokens)
            try:
                # max_retries=1 on instructor: a single *validation* retry for
                # malformed JSON. This is free (no HTTP call) and unrelated to
                # rate limiting.
                # `create_with_completion` returns (parsed_model, raw_completion);
                # the raw completion carries `.usage` for token accounting.
                response, completion = await self.client.chat.completions.create_with_completion(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": text},
                    ],
                    temperature=self.temperature,
                    max_tokens=self.max_tokens,
                    response_model=SentimentResponse,
                    max_retries=1,
                )
                return response, completion
            except Exception as exc:
                outcome, underlying = _classify(exc)
                last_outcome = outcome

                if outcome is _Outcome.RATE_LIMIT:
                    retry_after = _parse_retry_after(
                        underlying or exc,
                        fallback=_backoff(
                            attempt, self.initial_backoff, self.max_backoff
                        ),
                    )
                    pause = max(self.min_pause, retry_after * self.pause_padding)
                    last_retry_after = pause
                    self.rate_limiter.notify_rate_limited(pause)
                    continue

                if outcome is _Outcome.TRANSIENT:
                    last_transient = underlying or exc
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
            await self._raw_client.models.list()
            return True
        except Exception:
            return False


def _estimate_tokens(system_prompt: str, text: str, max_output: int) -> int:
    """Rough estimator: ~4 chars per token, plus the max output budget."""
    chars = len(system_prompt) + len(text)
    return (chars // 4) + max_output


_TRANSIENT_SDK_TYPES = (APIConnectionError, APITimeoutError, InternalServerError)


def _classify(exc: Exception) -> tuple[_Outcome, Exception | None]:
    """Classify an outbound-call failure and return (outcome, underlying).

    ``underlying`` is the unwrapped SDK exception when the failure originated
    from the HTTP layer (possibly via ``InstructorRetryException``), or None
    for pure validation / unknown failures. Returning it lets the caller read
    Retry-After headers off the real response object.
    """
    sdk_exc = _unwrap_sdk_error(exc)

    if sdk_exc is not None:
        if _is_rate_limit(sdk_exc):
            return _Outcome.RATE_LIMIT, sdk_exc
        if isinstance(sdk_exc, _TRANSIENT_SDK_TYPES):
            return _Outcome.TRANSIENT, sdk_exc
        status = _status_code(sdk_exc)
        if status is not None and status >= 500:
            return _Outcome.TRANSIENT, sdk_exc
        if status is not None and 400 <= status < 500:
            return _Outcome.FATAL_CONFIG, sdk_exc
        # APIStatusError with no readable status — treat as fatal rather than
        # loop forever.
        return _Outcome.FATAL_CONFIG, sdk_exc

    # No SDK error anywhere in the cause chain. Instructor exhausting its own
    # validation retry (prose in `content`, schema mismatch, truncation) lands
    # here. Retrying at the HTTP level won't flip a prose-returning model into
    # a JSON-returning one — propagate.
    if isinstance(exc, InstructorRetryException):
        return _Outcome.FATAL_VALIDATION, None

    return _Outcome.UNKNOWN, None


def _unwrap_sdk_error(exc: Exception) -> Exception | None:
    """Walk __cause__/__context__ looking for a known OpenAI SDK exception.

    Instructor uses both ``raise X from Y`` (sets __cause__) and bare ``raise X``
    inside an ``except`` block (sets __context__), so we check both.
    """
    sdk_types = (
        RateLimitError,
        APIConnectionError,
        APITimeoutError,
        APIStatusError,
    )
    current: BaseException | None = exc
    seen: set[int] = set()
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        if isinstance(current, sdk_types):
            return current
        current = current.__cause__ or current.__context__
    return None


def _is_rate_limit(exc: Exception) -> bool:
    if isinstance(exc, RateLimitError):
        return True
    return _status_code(exc) == 429


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
    return min(maximum, initial * (2 ** (attempt - 1)))
