"""Regression tests for Settings' provider-default validator.

Devin review on PR #13 flagged that `if not values.get(field)` treated an
explicit 0 for `rate_rpm` / `rate_tpm` the same as "unset" and silently
overwrote it with the provider default. These tests pin the corrected
behavior: numeric 0 is preserved, empty strings and missing keys still get
the provider defaults applied.

Also pins the RunPod base_url/api_key validation: PROVIDER_DEFAULTS has no
base_url for RunPod (per-endpoint URLs), so the OpenAI SDK would silently
route to api.openai.com if LLM_BASE_URL were left empty. Settings now fails
loud at config load.
"""

import pytest
from pydantic import ValidationError

from config import Settings


def test_explicit_rate_rpm_zero_is_honored_for_groq():
    """A user setting LLM_RATE_RPM=0 must disable the RPM bucket, even when
    the Groq provider default would otherwise apply 28."""
    settings = Settings(
        provider="groq",
        api_key="test",
        rate_rpm=0,
    )
    assert settings.rate_rpm == 0
    # tpm was left unset → provider default must still apply
    assert settings.rate_tpm == 5800


def test_explicit_rate_tpm_zero_is_honored_for_groq():
    settings = Settings(
        provider="groq",
        api_key="test",
        rate_tpm=0,
    )
    assert settings.rate_tpm == 0
    assert settings.rate_rpm == 28


def test_groq_defaults_apply_when_rate_fields_unset():
    """Baseline: without explicit values, provider defaults fill in."""
    settings = Settings(provider="groq", api_key="test")
    assert settings.rate_rpm == 28
    assert settings.rate_tpm == 5800


def test_empty_string_base_url_still_gets_provider_default():
    """Preserve the existing `"" means unset` convention for string fields."""
    settings = Settings(provider="groq", api_key="test", base_url="")
    assert settings.base_url == "https://api.groq.com/openai/v1"


# ---------------------------------------------------------------------------
# RunPod-specific validation: silent fallback to api.openai.com is the bug
# we're guarding against here.
# ---------------------------------------------------------------------------


def test_runpod_missing_base_url_raises():
    with pytest.raises(ValidationError, match="LLM_BASE_URL"):
        Settings(provider="runpod", api_key="test", base_url="")


def test_runpod_non_runpod_base_url_raises():
    """Catches the case where env points at Ollama but provider=runpod."""
    with pytest.raises(ValidationError, match="runpod"):
        Settings(
            provider="runpod",
            api_key="test",
            base_url="http://localhost:11434/v1",
        )


def test_runpod_accepts_pod_proxy_url():
    """Pod proxy URLs use *.proxy.runpod.net, not runpod.ai. Both are valid."""
    settings = Settings(
        provider="runpod",
        api_key="test",
        base_url="https://abc123-8000.proxy.runpod.net/v1",
    )
    assert settings.base_url == "https://abc123-8000.proxy.runpod.net/v1"


def test_runpod_missing_api_key_raises():
    with pytest.raises(ValidationError, match="LLM_API_KEY"):
        Settings(
            provider="runpod",
            api_key="",
            base_url="https://api.runpod.ai/v2/abc/openai/v1",
        )


def test_runpod_valid_config_loads():
    settings = Settings(
        provider="runpod",
        api_key="rp_secret",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
    )
    assert settings.base_url == "https://api.runpod.ai/v2/abc/openai/v1"


# ---------------------------------------------------------------------------
# Per-provider HTTP timeouts. Hardcoding 60s for all providers caused spurious
# APITimeoutError on RunPod cold starts and large local Ollama generations.
# ---------------------------------------------------------------------------


def test_runpod_default_max_concurrency_is_32():
    """vLLM continuous batching handles tens of concurrent requests; the
    global default of 3 throttles the worker uselessly."""
    settings = Settings(
        provider="runpod",
        api_key="test",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
    )
    assert settings.max_concurrency == 32


def test_runpod_explicit_max_concurrency_is_honored():
    settings = Settings(
        provider="runpod",
        api_key="test",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
        max_concurrency=8,
    )
    assert settings.max_concurrency == 8


def test_runpod_default_request_timeout_is_600s():
    settings = Settings(
        provider="runpod",
        api_key="test",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
    )
    assert settings.request_timeout_s == 600.0


def test_ollama_default_request_timeout_is_300s():
    settings = Settings(provider="ollama")
    assert settings.request_timeout_s == 300.0


def test_groq_default_request_timeout_is_60s():
    settings = Settings(provider="groq", api_key="test")
    assert settings.request_timeout_s == 60.0


def test_explicit_request_timeout_is_honored():
    settings = Settings(
        provider="runpod",
        api_key="test",
        base_url="https://api.runpod.ai/v2/abc/openai/v1",
        request_timeout_s=120.0,
    )
    assert settings.request_timeout_s == 120.0
