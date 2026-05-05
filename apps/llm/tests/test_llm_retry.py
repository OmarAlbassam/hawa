"""Tests for the rate-limit retry loop in LLMClient.

The OpenAI SDK is mocked; we exercise only our wrapper's handling of 429s,
Retry-After parsing, and RateLimitExhaustedError.
"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest
from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AuthenticationError,
    BadRequestError,
    InternalServerError,
    RateLimitError,
)
from pydantic import ValidationError

from config import Settings
from models import AnalyzeRequest, SentimentResponse
from services.analyzer import AnalyzerService
from services.llm_client import LLMClient, RateLimitExhaustedError
from services.rate_limiter import ProviderRateLimiter


def _make_settings(**overrides) -> Settings:
    base = dict(
        provider="ollama",
        base_url="http://localhost:9999/v1",
        api_key="test",
        model="test-model",
        rate_rpm=0,
        rate_tpm=0,
        rate_max_retries=3,
        rate_initial_backoff_s=0.01,
        rate_max_backoff_s=0.05,
        # Disable the pause floor/padding for existing tests that assert on
        # raw Retry-After values. The dedicated tests below exercise them.
        rate_min_pause_s=0.0,
        rate_pause_padding=1.0,
        max_concurrency=2,
    )
    base.update(overrides)
    return Settings(**base)


def _rate_limit_error(retry_after: str | None = "0.05") -> RateLimitError:
    headers = {"retry-after": retry_after} if retry_after is not None else {}
    request = httpx.Request("POST", "http://localhost/chat")
    response = httpx.Response(429, headers=headers, request=request)
    return RateLimitError("rate limited", response=response, body=None)


def _ok_completion(
    *,
    score: float = 3.0,
    emotion: str = "JOY",
    aspect: str = "PRODUCT",
    prompt_tokens: int = 10,
    completion_tokens: int = 5,
    total_tokens: int = 15,
) -> MagicMock:
    """Build a fake ChatCompletion the SDK's `.create` would return.

    The `.choices[0].message.content` carries valid `SentimentResponse` JSON;
    `.usage` carries server-reported token counts.
    """
    payload = SentimentResponse(
        score=score, emotion=emotion, aspect=aspect
    ).model_dump_json()
    completion = MagicMock()
    completion.choices = [MagicMock()]
    completion.choices[0].message.content = payload
    completion.usage.prompt_tokens = prompt_tokens
    completion.usage.completion_tokens = completion_tokens
    completion.usage.total_tokens = total_tokens
    return completion


def _build_client(
    *, rate_limiter: ProviderRateLimiter | None = None, **setting_overrides
) -> tuple[LLMClient, AsyncMock]:
    settings = _make_settings(**setting_overrides)
    limiter = rate_limiter or ProviderRateLimiter(rpm=0, tpm=0)
    client = LLMClient(settings, limiter)
    create_mock = AsyncMock()
    client.client = MagicMock()
    client.client.chat.completions.create = create_mock
    return client, create_mock


async def test_succeeds_without_retry_when_no_429():
    client, create = _build_client()
    create.return_value = _ok_completion()

    result = await client.analyze("system", "hello")
    assert result.score == 3.0
    assert create.await_count == 1


async def test_recovers_after_transient_429():
    client, create = _build_client()
    create.side_effect = [_rate_limit_error("0.05"), _ok_completion()]

    result = await client.analyze("system", "hello")
    assert result.score == 3.0
    assert create.await_count == 2


async def test_notifies_limiter_with_retry_after():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [_rate_limit_error("1.5"), _ok_completion()]

    await client.analyze("s", "t")
    limiter.notify_rate_limited.assert_called_once_with(1.5)


async def test_falls_back_to_backoff_when_no_retry_after_header():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [_rate_limit_error(retry_after=None), _ok_completion()]

    await client.analyze("s", "t")
    # initial_backoff=0.01, attempt=1, jittered to [0.005, 0.015].
    limiter.notify_rate_limited.assert_called_once()
    pause = limiter.notify_rate_limited.call_args.args[0]
    assert 0.005 <= pause <= 0.015


async def test_pause_floor_overrides_small_retry_after():
    """Groq often returns Retry-After=1–2s after a TPM hit; that's only enough
    to refill *one* request. Without a floor, queued workers resume in lockstep
    and re-trigger the limit. The floor forces a real pause."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(
        rate_limiter=limiter,
        rate_min_pause_s=5.0,
        rate_pause_padding=1.0,
    )
    create.side_effect = [_rate_limit_error("1.0"), _ok_completion()]

    await client.analyze("s", "t")
    limiter.notify_rate_limited.assert_called_once_with(5.0)


async def test_pause_padding_scales_retry_after_above_floor():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(
        rate_limiter=limiter,
        rate_min_pause_s=0.0,
        rate_pause_padding=1.5,
    )
    create.side_effect = [_rate_limit_error("10.0"), _ok_completion()]

    await client.analyze("s", "t")
    limiter.notify_rate_limited.assert_called_once_with(15.0)


async def test_pause_floor_applies_to_missing_header_fallback():
    """The fallback backoff must also pass through the floor so behavior is
    consistent whether or not the server sent a Retry-After."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(
        rate_limiter=limiter,
        rate_initial_backoff_s=0.01,
        rate_min_pause_s=5.0,
        rate_pause_padding=1.0,
    )
    create.side_effect = [_rate_limit_error(retry_after=None), _ok_completion()]

    await client.analyze("s", "t")
    limiter.notify_rate_limited.assert_called_once_with(5.0)


async def test_raises_exhausted_error_after_max_retries():
    client, create = _build_client(rate_max_retries=2)
    create.side_effect = _rate_limit_error("0.01")

    with pytest.raises(RateLimitExhaustedError) as exc:
        await client.analyze("s", "t")
    assert exc.value.attempts == 2
    assert create.await_count == 2


async def test_handles_api_status_error_with_429():
    client, create = _build_client()
    request = httpx.Request("POST", "http://localhost/chat")
    response = httpx.Response(429, headers={"retry-after": "0.02"}, request=request)
    status_err = APIStatusError("rate limited", response=response, body=None)
    create.side_effect = [status_err, _ok_completion()]

    await client.analyze("s", "t")
    assert create.await_count == 2


async def test_persistent_5xx_propagates_after_retry_budget():
    client, create = _build_client(rate_max_retries=3)
    request = httpx.Request("POST", "http://localhost/chat")
    response = httpx.Response(500, request=request)
    status_err = APIStatusError("server error", response=response, body=None)
    create.side_effect = status_err

    with pytest.raises(APIStatusError):
        await client.analyze("s", "t")
    assert create.await_count == 3


async def test_analyzer_converts_exhaustion_to_failed_result():
    client, create = _build_client(rate_max_retries=1)
    create.side_effect = _rate_limit_error("0.01")
    settings = _make_settings()
    analyzer = AnalyzerService(client, settings)

    response = await analyzer.analyze_batch(
        [AnalyzeRequest(post_id=42, text="hi")],
    )
    assert response.results == []
    assert len(response.failed) == 1
    assert response.failed[0].post_id == 42
    assert response.failed[0].error.startswith("rate_limited:")


async def test_analyzer_succeeds_after_transient_429():
    client, create = _build_client()
    create.side_effect = [_rate_limit_error("0.01"), _ok_completion()]
    settings = _make_settings()
    analyzer = AnalyzerService(client, settings)

    response = await analyzer.analyze_batch(
        [AnalyzeRequest(post_id=7, text="hi")],
    )
    assert len(response.results) == 1
    assert response.failed == []
    assert response.results[0].post_id == 7


# ---------------------------------------------------------------------------
# Non-429 transient + fatal classifications.
# ---------------------------------------------------------------------------


def _api_status_error(status: int, retry_after: str | None = None) -> APIStatusError:
    headers = {"retry-after": retry_after} if retry_after is not None else {}
    request = httpx.Request("POST", "http://localhost/chat")
    response = httpx.Response(status, headers=headers, request=request)
    return APIStatusError(f"status {status}", response=response, body=None)


async def test_recovers_from_transient_500():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [
        InternalServerError(
            "boom",
            response=httpx.Response(
                500, request=httpx.Request("POST", "http://localhost/chat")
            ),
            body=None,
        ),
        _ok_completion(),
    ]

    result = await client.analyze("s", "t")
    assert result.score == 3.0
    assert create.await_count == 2
    # Transient errors must not trip the shared pause gate.
    limiter.notify_rate_limited.assert_not_called()


async def test_recovers_from_api_timeout_error():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [
        APITimeoutError(request=httpx.Request("POST", "http://localhost/chat")),
        _ok_completion(),
    ]

    await client.analyze("s", "t")
    assert create.await_count == 2
    limiter.notify_rate_limited.assert_not_called()


async def test_recovers_from_connection_error():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [
        APIConnectionError(
            request=httpx.Request("POST", "http://localhost/chat"),
        ),
        _ok_completion(),
    ]

    await client.analyze("s", "t")
    assert create.await_count == 2
    limiter.notify_rate_limited.assert_not_called()


async def test_connection_error_exhaustion_propagates_as_apiconnectionerror():
    client, create = _build_client(rate_max_retries=2)
    create.side_effect = APIConnectionError(
        request=httpx.Request("POST", "http://localhost/chat"),
    )

    with pytest.raises(APIConnectionError):
        await client.analyze("s", "t")
    assert create.await_count == 2


async def test_auth_error_is_fatal_no_retry():
    client, create = _build_client()
    create.side_effect = AuthenticationError(
        "bad key",
        response=httpx.Response(
            401, request=httpx.Request("POST", "http://localhost/chat")
        ),
        body=None,
    )

    with pytest.raises(AuthenticationError):
        await client.analyze("s", "t")
    assert create.await_count == 1


async def test_bad_request_is_fatal_no_retry():
    client, create = _build_client()
    create.side_effect = BadRequestError(
        "nope",
        response=httpx.Response(
            400, request=httpx.Request("POST", "http://localhost/chat")
        ),
        body=None,
    )

    with pytest.raises(BadRequestError):
        await client.analyze("s", "t")
    assert create.await_count == 1


async def test_malformed_json_is_fatal():
    """Schema/JSON parse failures aren't fixed by retrying — propagate."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    bad_completion = MagicMock()
    bad_completion.choices = [MagicMock()]
    bad_completion.choices[0].message.content = "not valid json {{"
    create.return_value = bad_completion

    with pytest.raises(ValueError):  # JSONDecodeError is a ValueError subclass
        await client.analyze("s", "t")
    assert create.await_count == 1
    limiter.notify_rate_limited.assert_not_called()


async def test_schema_mismatch_is_fatal():
    """Valid JSON, wrong shape — pydantic.ValidationError must propagate."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    bad_completion = MagicMock()
    bad_completion.choices = [MagicMock()]
    bad_completion.choices[0].message.content = (
        '{"score": "definitely not a number-like string"}'
    )
    create.return_value = bad_completion

    with pytest.raises(ValidationError):
        await client.analyze("s", "t")
    assert create.await_count == 1
    limiter.notify_rate_limited.assert_not_called()


async def test_api_status_error_5xx_is_transient():
    """APIStatusError (not a typed subclass) with status>=500 retries locally."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [_api_status_error(502), _ok_completion()]

    await client.analyze("s", "t")
    assert create.await_count == 2
    limiter.notify_rate_limited.assert_not_called()


# ---------------------------------------------------------------------------
# Per-(provider, model) extra_body assembly.
# ---------------------------------------------------------------------------


def test_ollama_non_qwen_attaches_format_schema_only():
    """Ollama 0.5+ enforces a JSON schema via the `format` field on extra_body."""
    client, _ = _build_client()
    assert client.extra_body is not None
    assert set(client.extra_body.keys()) == {"format"}
    assert "properties" in client.extra_body["format"]


def test_qwen3_on_ollama_disables_thinking_and_attaches_format():
    client, _ = _build_client(model="qwen3-8b")
    assert client.extra_body["chat_template_kwargs"] == {"enable_thinking": False}
    # Format schema is still attached so Ollama enforces enum membership.
    assert "format" in client.extra_body


def test_runpod_injects_guided_json():
    """vLLM guided decoding constrains output to SentimentResponse's schema."""
    client, _ = _build_client(
        provider="runpod",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
        api_key="rp_secret",
        model="meta-llama/Llama-3.1-8B-Instruct",
    )
    assert "guided_json" in client.extra_body
    schema = client.extra_body["guided_json"]
    assert "properties" in schema
    assert "score" in schema["properties"]
    assert "emotion" in schema["properties"]
    # Enum refs must resolve to $defs for outlines/xgrammar.
    assert "$defs" in schema
    assert "Emotion" in schema["$defs"]


def test_runpod_qwen3_merges_thinking_and_guided_json():
    client, _ = _build_client(
        provider="runpod",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
        api_key="rp_secret",
        model="qwen3-32b",
    )
    assert client.extra_body["chat_template_kwargs"] == {"enable_thinking": False}
    assert "guided_json" in client.extra_body


def test_groq_qwen3_uses_reasoning_effort_none_not_chat_template_kwargs():
    """Groq rejects ``chat_template_kwargs`` with 400, and qwen3's default
    reasoning eats the entire ``max_tokens`` budget so no JSON is produced
    (json_validate_failed). The fix is Groq's ``reasoning_effort: "none"``."""
    client, _ = _build_client(
        provider="groq",
        base_url="https://api.groq.com/openai/v1",
        api_key="gsk_secret",
        model="qwen/qwen3-32b",
    )
    assert client.extra_body is not None
    assert "chat_template_kwargs" not in client.extra_body
    assert client.extra_body["reasoning_effort"] == "none"


def test_groq_non_qwen_does_not_set_reasoning_effort():
    """The reasoning_effort=none knob is qwen3-specific. Don't send it to
    other Groq models that may reject unknown parameters."""
    client, _ = _build_client(
        provider="groq",
        base_url="https://api.groq.com/openai/v1",
        api_key="gsk_secret",
        model="llama-3.1-8b-instant",
    )
    if client.extra_body is not None:
        assert "reasoning_effort" not in client.extra_body


async def test_extra_body_is_forwarded_on_chat_create():
    """Whatever extra_body LLMClient computes, it must reach the SDK call."""
    client, create = _build_client(
        provider="runpod",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
        api_key="rp_secret",
        model="meta-llama/Llama-3.1-8B-Instruct",
    )
    create.return_value = _ok_completion()

    await client.analyze("s", "t")

    assert create.await_count == 1
    kwargs = create.await_args.kwargs
    assert "extra_body" in kwargs
    assert "guided_json" in kwargs["extra_body"]


# ---------------------------------------------------------------------------
# Per-(provider, model) response_format selection.
# ---------------------------------------------------------------------------


def test_ollama_format_schema_attached_to_extra_body():
    """Ollama 0.5+ enforces a JSON schema via the `format` field."""
    client, _ = _build_client(model="llama3.1:8b")
    assert client.extra_body is not None
    assert "format" in client.extra_body
    assert client.extra_body["format"]["properties"].keys() >= {
        "is_relevant", "score", "emotion", "aspect"
    }


def test_ollama_uses_plain_json_object_response_format():
    """Schema enforcement happens via extra_body['format']; response_format stays simple."""
    client, _ = _build_client(model="llama3.1:8b")
    assert client.response_format == {"type": "json_object"}


def test_runpod_uses_plain_json_object_response_format():
    """vLLM enforcement happens via extra_body['guided_json']; response_format stays simple."""
    client, _ = _build_client(
        provider="runpod",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
        api_key="rp_secret",
        model="meta-llama/Llama-3.1-8B-Instruct",
    )
    assert client.response_format == {"type": "json_object"}


def test_groq_unsupported_model_uses_json_object():
    """Llama-3.x on Groq has no schema mode; coercion layer carries the guarantee."""
    client, _ = _build_client(provider="groq", model="llama-3.1-8b-instant")
    assert client.response_format == {"type": "json_object"}


def test_groq_best_effort_model_uses_json_schema_non_strict():
    """Llama 4 Scout supports json_schema best-effort but not strict."""
    client, _ = _build_client(
        provider="groq", model="meta-llama/llama-4-scout-17b-16e-instruct"
    )
    rf = client.response_format
    assert rf["type"] == "json_schema"
    assert rf["json_schema"]["strict"] is False
    assert rf["json_schema"]["name"] == "sentiment_response"


def test_groq_strict_model_uses_json_schema_strict():
    """GPT-OSS supports strict json_schema — true constrained decoding."""
    client, _ = _build_client(provider="groq", model="openai/gpt-oss-20b")
    rf = client.response_format
    assert rf["type"] == "json_schema"
    assert rf["json_schema"]["strict"] is True
    schema = rf["json_schema"]["schema"]
    # Strict mode requires every property to be in `required` and
    # `additionalProperties: false` on every object.
    assert schema["additionalProperties"] is False
    assert set(schema["required"]) == set(schema["properties"].keys())


async def test_response_format_is_forwarded_on_chat_create():
    client, create = _build_client(
        provider="groq", model="meta-llama/llama-4-scout-17b-16e-instruct"
    )
    create.return_value = _ok_completion()

    await client.analyze("s", "t")

    kwargs = create.await_args.kwargs
    assert kwargs["response_format"]["type"] == "json_schema"
