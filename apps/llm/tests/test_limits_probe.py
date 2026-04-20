"""Tests for rate-limit auto-discovery from x-ratelimit-* response headers."""

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from services.limits_probe import (
    DiscoveredLimits,
    _classify_rpm,
    _classify_tpm,
    _parse_duration,
    _parse_int,
    discover_limits,
)


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("60", 60.0),
        ("59.56", 59.56),
        ("45s", 45.0),
        ("1m30s", 90.0),
        ("7m59.56s", 7 * 60 + 59.56),
        ("1h", 3600.0),
        ("1h30m", 5400.0),
        (None, None),
        ("", None),
        ("garbage", None),
    ],
)
def test_parse_duration(raw, expected):
    assert _parse_duration(raw) == expected


@pytest.mark.parametrize(
    "raw,expected",
    [("30", 30), ("0", 0), (None, None), ("oops", None)],
)
def test_parse_int(raw, expected):
    assert _parse_int(raw) == expected


def test_classify_rpm_accepts_small_values_with_short_reset():
    rpm, note = _classify_rpm(30, reset_seconds=45.0)
    assert rpm == 30
    assert "30/min" in note


def test_classify_rpm_accepts_when_reset_is_absent_and_magnitude_is_sane():
    rpm, _ = _classify_rpm(30, reset_seconds=None)
    assert rpm == 30


def test_classify_rpm_rejects_daily_magnitude_even_without_reset():
    # Groq free llama: 14,400 requests/day masquerading as a rate-limit header.
    rpm, note = _classify_rpm(14_400, reset_seconds=None)
    assert rpm is None
    assert "daily" in note or "too large" in note


def test_classify_rpm_rejects_when_reset_indicates_long_window():
    # Groq compound-mini: 250 RPD shows up as limit=250, reset=345.6s.
    # Magnitude alone (250 < 2000) would wrongly accept it as RPM.
    rpm, note = _classify_rpm(250, reset_seconds=345.6)
    assert rpm is None
    assert "longer" in note


def test_classify_rpm_handles_missing_limit():
    rpm, note = _classify_rpm(None, reset_seconds=60.0)
    assert rpm is None
    assert "missing" in note


def test_classify_tpm_uses_value_as_is_with_short_reset():
    tpm, note = _classify_tpm(6000, reset_seconds=30.0)
    assert tpm == 6000
    assert "6000/min" in note


def test_classify_tpm_uses_value_when_reset_absent():
    tpm, _ = _classify_tpm(70_000, reset_seconds=None)
    assert tpm == 70_000


def test_classify_tpm_rejects_when_reset_indicates_long_window():
    tpm, note = _classify_tpm(70_000, reset_seconds=300.0)
    assert tpm is None
    assert "longer" in note


def test_classify_tpm_handles_missing():
    tpm, _ = _classify_tpm(None, reset_seconds=None)
    assert tpm is None


def _mock_client_with_headers(
    models_headers: dict, chat_headers: dict | None = None
) -> MagicMock:
    client = MagicMock()
    client.models.with_raw_response.list = AsyncMock(
        return_value=SimpleNamespace(headers=models_headers)
    )
    if chat_headers is not None:
        client.chat.completions.with_raw_response.create = AsyncMock(
            return_value=SimpleNamespace(headers=chat_headers)
        )
    return client


async def test_discover_limits_reads_groq_headers():
    headers = {
        "x-ratelimit-limit-requests": "30",
        "x-ratelimit-limit-tokens": "6000",
        "x-ratelimit-reset-requests": "59s",
        "x-ratelimit-reset-tokens": "1m",
    }
    client = _mock_client_with_headers(headers)

    result = await discover_limits(client)
    assert result == DiscoveredLimits(rpm=30, tpm=6000)


async def test_discover_limits_skips_daily_rpm_from_long_reset():
    # groq/compound-mini pattern: 250 RPD advertised as limit=250, reset=345.6s.
    headers = {
        "x-ratelimit-limit-requests": "250",
        "x-ratelimit-limit-tokens": "70000",
        "x-ratelimit-reset-requests": "5m45.6s",
        "x-ratelimit-reset-tokens": "",
    }
    client = _mock_client_with_headers(headers)

    result = await discover_limits(client)
    assert result.rpm is None
    assert result.tpm == 70_000


async def test_discover_limits_skips_daily_rpm_keeps_tpm():
    # Groq free-tier sends 14400 RPD as x-ratelimit-limit-requests; don't
    # let it pose as 14400 RPM. But tpm of 6000/min should still apply.
    headers = {
        "x-ratelimit-limit-requests": "14400",
        "x-ratelimit-limit-tokens": "6000",
        "x-ratelimit-reset-requests": "6s",
        "x-ratelimit-reset-tokens": "",
    }
    client = _mock_client_with_headers(headers)

    result = await discover_limits(client)
    assert result.rpm is None
    assert result.tpm == 6000


async def test_discover_limits_returns_empty_on_probe_failure():
    client = MagicMock()
    client.models.with_raw_response.list = AsyncMock(
        side_effect=RuntimeError("network down")
    )

    result = await discover_limits(client)
    assert result.empty


async def test_discover_limits_returns_empty_when_headers_absent():
    client = _mock_client_with_headers({})
    result = await discover_limits(client)
    assert result.empty


async def test_discover_limits_falls_back_to_chat_when_models_has_no_headers():
    client = _mock_client_with_headers(
        models_headers={},
        chat_headers={
            "x-ratelimit-limit-requests": "30",
            "x-ratelimit-limit-tokens": "6000",
            "x-ratelimit-reset-requests": "59s",
            "x-ratelimit-reset-tokens": "1m",
        },
    )
    result = await discover_limits(client, model="llama-3.1-8b-instant")
    assert result == DiscoveredLimits(rpm=30, tpm=6000)
    client.chat.completions.with_raw_response.create.assert_awaited_once()


async def test_discover_limits_skips_chat_fallback_without_model():
    client = _mock_client_with_headers(
        models_headers={},
        chat_headers={"x-ratelimit-limit-requests": "30"},
    )
    result = await discover_limits(client, model=None)
    assert result.empty
    client.chat.completions.with_raw_response.create.assert_not_called()


async def test_discover_limits_prefers_models_when_it_has_headers():
    client = _mock_client_with_headers(
        models_headers={"x-ratelimit-limit-requests": "30"},
        chat_headers={},
    )
    result = await discover_limits(client, model="anything")
    assert result.rpm == 30
    client.chat.completions.with_raw_response.create.assert_not_called()
