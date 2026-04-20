"""Regression tests for main._apply_discovered.

Narrow coverage for a single truthy-check fix flagged in PR #13 review:
a provider that reports `rpm=0` (unlimited) must have that value applied,
not silently replaced by the configured default.
"""

import logging

from config import Settings
from main import _apply_discovered
from services.limits_probe import DiscoveredLimits
from services.rate_limiter import ProviderRateLimiter


def _settings() -> Settings:
    return Settings(
        provider="groq",
        api_key="test",
        rate_rpm=28,
        rate_tpm=5800,
    )


def test_discovered_zero_rpm_is_honored_not_replaced_by_default():
    """A discovered rpm=0 means 'unlimited' — it must win over settings.rate_rpm."""
    current = ProviderRateLimiter(rpm=28, tpm=5800)
    applied = _apply_discovered(
        _settings(),
        current,
        DiscoveredLimits(rpm=0, tpm=10000),
        logging.getLogger("test"),
    )
    assert applied.configured_rpm == 0
    # margin (0.9) × 10000 = 9000
    assert applied.configured_tpm == 9000


def test_discovered_none_falls_back_to_configured_default():
    """Baseline: if a limit is absent from the headers, keep the configured default."""
    current = ProviderRateLimiter(rpm=28, tpm=5800)
    applied = _apply_discovered(
        _settings(),
        current,
        DiscoveredLimits(rpm=None, tpm=10000),
        logging.getLogger("test"),
    )
    assert applied.configured_rpm == 28
    assert applied.configured_tpm == 9000


def test_empty_discovered_returns_current_limiter_unchanged():
    current = ProviderRateLimiter(rpm=28, tpm=5800)
    applied = _apply_discovered(
        _settings(),
        current,
        DiscoveredLimits(),
        logging.getLogger("test"),
    )
    assert applied is current
