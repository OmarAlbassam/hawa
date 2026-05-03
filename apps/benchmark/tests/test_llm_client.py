"""Tests for `LLMClient.analyze_with_usage` token capture.

Mocks instructor's `create_with_completion` to return a fake completion with
known `usage` values; asserts the wrapper extracts them faithfully.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from config import Settings
from models import SentimentResponse
from services.llm_client import LLMClient, TokenUsage
from services.rate_limiter import ProviderRateLimiter


def _make_client(create_with_completion_mock: AsyncMock) -> LLMClient:
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
    client.client.chat.completions.create_with_completion = create_with_completion_mock
    return client


def _fake_completion(prompt: int, completion: int, total: int) -> MagicMock:
    completion_obj = MagicMock()
    completion_obj.usage.prompt_tokens = prompt
    completion_obj.usage.completion_tokens = completion
    completion_obj.usage.total_tokens = total
    return completion_obj


async def test_analyze_with_usage_returns_token_counts():
    parsed = SentimentResponse(score=4.0, emotion="JOY", aspect="PRODUCT")
    completion = _fake_completion(prompt=120, completion=42, total=162)
    mock = AsyncMock(return_value=(parsed, completion))
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
    completion = MagicMock()
    completion.usage = None
    mock = AsyncMock(return_value=(parsed, completion))
    client = _make_client(mock)

    response, usage = await client.analyze_with_usage("system", "hello")

    assert response.score == 3.0
    assert usage is None


async def test_analyze_still_returns_just_response():
    # The original `analyze()` path must continue returning bare SentimentResponse
    # so production analyzer.py callers don't need to unpack a tuple.
    parsed = SentimentResponse(score=2.5, emotion="NEUTRAL", aspect="SERVICE")
    mock = AsyncMock(return_value=(parsed, _fake_completion(1, 1, 2)))
    client = _make_client(mock)

    response = await client.analyze("system", "hi")
    assert response.score == 2.5
    assert response.emotion.value == "NEUTRAL"
