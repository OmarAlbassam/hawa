"""Tests for the rate-limit retry loop in LLMClient.

The OpenAI/instructor stack is mocked; we exercise only our wrapper's
handling of 429s, Retry-After parsing, and RateLimitExhaustedError.
"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest
from instructor.core.exceptions import InstructorRetryException
from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AuthenticationError,
    BadRequestError,
    InternalServerError,
    RateLimitError,
)

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


def _ok_response() -> SentimentResponse:
    return SentimentResponse(score=3.0, emotion="JOY", aspect="PRODUCT")


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
    create.return_value = _ok_response()

    result = await client.analyze("system", "hello")
    assert result.score == 3.0
    assert create.await_count == 1


async def test_recovers_after_transient_429():
    client, create = _build_client()
    create.side_effect = [_rate_limit_error("0.05"), _ok_response()]

    result = await client.analyze("system", "hello")
    assert result.score == 3.0
    assert create.await_count == 2


async def test_notifies_limiter_with_retry_after():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [_rate_limit_error("1.5"), _ok_response()]

    await client.analyze("s", "t")
    limiter.notify_rate_limited.assert_called_once_with(1.5)


async def test_falls_back_to_backoff_when_no_retry_after_header():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [_rate_limit_error(retry_after=None), _ok_response()]

    await client.analyze("s", "t")
    # initial_backoff=0.01, attempt=1 => 0.01
    limiter.notify_rate_limited.assert_called_once_with(0.01)


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
    create.side_effect = [_rate_limit_error("1.0"), _ok_response()]

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
    create.side_effect = [_rate_limit_error("10.0"), _ok_response()]

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
    create.side_effect = [_rate_limit_error(retry_after=None), _ok_response()]

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
    create.side_effect = [status_err, _ok_response()]

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
    create.side_effect = [_rate_limit_error("0.01"), _ok_response()]
    settings = _make_settings()
    analyzer = AnalyzerService(client, settings)

    response = await analyzer.analyze_batch(
        [AnalyzeRequest(post_id=7, text="hi")],
    )
    assert len(response.results) == 1
    assert response.failed == []
    assert response.results[0].post_id == 7


# ---------------------------------------------------------------------------
# Instructor wrapping + non-429 transient + fatal classifications.
# ---------------------------------------------------------------------------


def _instructor_wrapped(inner: Exception | None, *, use_context: bool = False) -> InstructorRetryException:
    """Build an InstructorRetryException the way instructor raises one.

    Real instructor sets __cause__ (via `raise ... from ...`) most of the time,
    but a few code paths use bare `raise` inside an `except` which only sets
    __context__. The classifier must handle either.
    """
    wrapper = InstructorRetryException(
        "retry exhausted", n_attempts=1, total_usage=0
    )
    if inner is not None:
        if use_context:
            wrapper.__context__ = inner
        else:
            wrapper.__cause__ = inner
    return wrapper


def _api_status_error(status: int, retry_after: str | None = None) -> APIStatusError:
    headers = {"retry-after": retry_after} if retry_after is not None else {}
    request = httpx.Request("POST", "http://localhost/chat")
    response = httpx.Response(status, headers=headers, request=request)
    return APIStatusError(f"status {status}", response=response, body=None)


async def test_recovers_from_instructor_wrapped_429():
    """Regression: instructor wraps 429s so `except RateLimitError` misses them."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [
        _instructor_wrapped(_rate_limit_error("0.05")),
        _ok_response(),
    ]

    result = await client.analyze("s", "t")
    assert result.score == 3.0
    assert create.await_count == 2
    limiter.notify_rate_limited.assert_called_once_with(0.05)


async def test_exhausts_retries_on_wrapped_429():
    client, create = _build_client(rate_max_retries=2)
    create.side_effect = _instructor_wrapped(_rate_limit_error("0.01"))

    with pytest.raises(RateLimitExhaustedError) as exc:
        await client.analyze("s", "t")
    assert exc.value.attempts == 2
    assert create.await_count == 2


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
        _ok_response(),
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
        _ok_response(),
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
        _ok_response(),
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


async def test_json_validation_failure_is_fatal():
    """Instructor raised its own retry-exhausted with no SDK cause underneath."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = _instructor_wrapped(None)

    with pytest.raises(InstructorRetryException):
        await client.analyze("s", "t")
    assert create.await_count == 1
    limiter.notify_rate_limited.assert_not_called()


async def test_wrapped_via_context_not_cause():
    """Some instructor paths use bare `raise` inside `except` — __context__ only."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [
        _instructor_wrapped(_rate_limit_error("0.03"), use_context=True),
        _ok_response(),
    ]

    await client.analyze("s", "t")
    assert create.await_count == 2
    limiter.notify_rate_limited.assert_called_once_with(0.03)


async def test_api_status_error_5xx_is_transient():
    """APIStatusError (not a typed subclass) with status>=500 retries locally."""
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited = MagicMock()
    client, create = _build_client(rate_limiter=limiter)
    create.side_effect = [_api_status_error(502), _ok_response()]

    await client.analyze("s", "t")
    assert create.await_count == 2
    limiter.notify_rate_limited.assert_not_called()
