"""Tests for `LLMClient.analyze_with_usage` token capture.

Mocks the OpenAI SDK's `chat.completions.create` to return a fake completion
with known `usage` values; asserts the wrapper extracts them faithfully.
"""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from config import Settings
from models import SentimentResponse
from services.llm_client import LLMClient, TokenUsage
from services.rate_limiter import ProviderRateLimiter


def _make_client(create_mock: AsyncMock) -> LLMClient:
    settings = Settings(
        provider="ollama",
        base_url="http://localhost:9999/v1",
        api_key="test",
        model="test-model",
        rate_rpm=0,
        rate_tpm=0,
        rate_max_retries=2,
        rate_initial_backoff_s=0.01,
        rate_max_backoff_s=0.05,
        rate_min_pause_s=0.0,
        rate_pause_padding=1.0,
        max_concurrency=1,
    )
    client = LLMClient(settings, ProviderRateLimiter(rpm=0, tpm=0))
    client.client = MagicMock()
    client.client.chat.completions.create = create_mock
    return client


def _fake_completion(
    parsed: SentimentResponse,
    *,
    prompt: int | None,
    completion: int | None,
    total: int | None,
    cached: int | None = None,
) -> MagicMock:
    completion_obj = MagicMock()
    completion_obj.choices = [MagicMock()]
    completion_obj.choices[0].message.content = parsed.model_dump_json()
    if prompt is None and completion is None and total is None:
        completion_obj.usage = None
    else:
        completion_obj.usage.prompt_tokens = prompt
        completion_obj.usage.completion_tokens = completion
        completion_obj.usage.total_tokens = total
        if cached is None:
            # Default MagicMock attribute access returns a child mock; force
            # the real path to "no details" so _extract_cached_tokens
            # returns None instead of receiving a fabricated count.
            completion_obj.usage.prompt_tokens_details = None
        else:
            completion_obj.usage.prompt_tokens_details = SimpleNamespace(
                cached_tokens=cached
            )
    return completion_obj


async def test_analyze_with_usage_returns_token_counts():
    parsed = SentimentResponse(score=4.0, emotion="JOY", aspect="PRODUCT")
    completion = _fake_completion(parsed, prompt=120, completion=42, total=162)
    mock = AsyncMock(return_value=completion)
    client = _make_client(mock)

    response, usage = await client.analyze_with_usage("system", "hello")

    assert response.score == 4.0
    assert isinstance(usage, TokenUsage)
    assert usage.prompt_tokens == 120
    assert usage.completion_tokens == 42
    assert usage.total_tokens == 162


async def test_analyze_with_usage_returns_none_when_completion_lacks_usage():
    # Self-hosted backends (some Ollama configs) don't include a usage field;
    # the wrapper must surface that as None rather than crash.
    parsed = SentimentResponse(score=3.0, emotion="NEUTRAL", aspect="PRODUCT")
    completion = _fake_completion(parsed, prompt=None, completion=None, total=None)
    mock = AsyncMock(return_value=completion)
    client = _make_client(mock)

    response, usage = await client.analyze_with_usage("system", "hello")

    assert response.score == 3.0
    assert usage is None


async def test_analyze_with_usage_captures_cached_tokens():
    parsed = SentimentResponse(score=4.0, emotion="JOY", aspect="PRODUCT")
    completion = _fake_completion(parsed, prompt=300, completion=20, total=320, cached=270)
    mock = AsyncMock(return_value=completion)
    client = _make_client(mock)

    _, usage = await client.analyze_with_usage("system", "hello")

    assert usage is not None
    assert usage.cached_tokens == 270


async def test_analyze_with_usage_cached_tokens_none_when_provider_omits_field():
    parsed = SentimentResponse(score=4.0, emotion="JOY", aspect="PRODUCT")
    completion = _fake_completion(parsed, prompt=120, completion=42, total=162)
    mock = AsyncMock(return_value=completion)
    client = _make_client(mock)

    _, usage = await client.analyze_with_usage("system", "hello")

    assert usage is not None
    assert usage.cached_tokens is None


async def test_session_id_forwarded_as_session_affinity_header():
    parsed = SentimentResponse(score=3.0, emotion="JOY", aspect="PRODUCT")
    mock = AsyncMock(
        return_value=_fake_completion(parsed, prompt=10, completion=5, total=15)
    )
    client = _make_client(mock)

    await client.analyze("system", "hi", session_id="exp-42")

    kwargs = mock.await_args.kwargs
    assert kwargs.get("extra_headers") == {"x-session-affinity": "exp-42"}


async def test_no_session_id_omits_extra_headers():
    parsed = SentimentResponse(score=3.0, emotion="JOY", aspect="PRODUCT")
    mock = AsyncMock(
        return_value=_fake_completion(parsed, prompt=10, completion=5, total=15)
    )
    client = _make_client(mock)

    await client.analyze("system", "hi")

    kwargs = mock.await_args.kwargs
    assert "extra_headers" not in kwargs


def _make_client_with_settings(**setting_overrides) -> LLMClient:
    base = dict(
        provider="ollama",
        base_url="http://localhost:9999/v1",
        api_key="test",
        model="test-model",
        rate_rpm=0,
        rate_tpm=0,
        max_concurrency=1,
    )
    base.update(setting_overrides)
    return LLMClient(Settings(**base), ProviderRateLimiter(rpm=0, tpm=0))


def test_extra_body_is_none_when_no_provider_or_model_extras_apply():
    """Fireworks + a non-qwen3 model has no auto-additions; with no
    reasoning_effort set, extra_body should be None."""
    client = _make_client_with_settings(
        provider="fireworks",
        api_key="test",
        base_url="https://api.fireworks.ai/inference/v1",
        model="accounts/fireworks/models/llama-v3p3-70b-instruct",
    )
    assert client.extra_body is None


def test_reasoning_effort_flows_into_extra_body():
    client = _make_client_with_settings(
        provider="fireworks",
        api_key="test",
        base_url="https://api.fireworks.ai/inference/v1",
        model="accounts/fireworks/models/gpt-oss-20b",
        reasoning_effort="low",
    )
    assert client.extra_body == {"reasoning_effort": "low"}


def test_qwen3_thinking_and_reasoning_effort_compose():
    client = _make_client_with_settings(
        provider="fireworks",
        api_key="test",
        base_url="https://api.fireworks.ai/inference/v1",
        model="qwen3-32b",
        reasoning_effort="medium",
    )
    assert client.extra_body == {
        "chat_template_kwargs": {"enable_thinking": False},
        "reasoning_effort": "medium",
    }


def test_max_tokens_setting_is_used_for_request_kwarg():
    client = _make_client_with_settings(max_tokens=2048)
    assert client.max_tokens == 2048


async def test_analyze_still_returns_just_response():
    # The original `analyze()` path must continue returning bare SentimentResponse
    # so production analyzer.py callers don't need to unpack a tuple.
    parsed = SentimentResponse(score=2.5, emotion="NEUTRAL", aspect="SERVICE")
    completion = _fake_completion(parsed, prompt=1, completion=1, total=2)
    mock = AsyncMock(return_value=completion)
    client = _make_client(mock)

    response = await client.analyze("system", "hi")
    assert response.score == 2.5
    assert response.emotion.value == "NEUTRAL"
