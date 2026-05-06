"""Regression tests for Settings' provider-default validator.

Devin review on PR #13 flagged that `if not values.get(field)` treated an
explicit 0 for `rate_rpm` / `rate_tpm` the same as "unset" and silently
overwrote it with the provider default. These tests pin the corrected
behavior: numeric 0 is preserved, empty strings and missing keys still get
the provider defaults applied.
"""

from config import Provider, Settings


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


def test_reasoning_effort_unset_by_default():
    """Reasoning-effort is opt-in; non-reasoning models must not get the field."""
    settings = Settings(provider="ollama", api_key="test")
    assert settings.reasoning_effort is None


def test_reasoning_effort_round_trips_explicit_value():
    settings = Settings(
        provider="fireworks",
        api_key="test",
        model="accounts/fireworks/models/gpt-oss-20b",
        reasoning_effort="low",
    )
    assert settings.reasoning_effort == "low"


def test_fireworks_defaults_resolve():
    """Fireworks ships a fixed base_url and disables the RPM/TPM buckets;
    the model slug must come from the caller. base_url="" mirrors the
    `"" means unset` convention so a developer's local .env (which may
    point at RunPod) doesn't shadow the provider default during tests."""
    settings = Settings(
        provider="fireworks",
        api_key="test",
        base_url="",
        model="accounts/fireworks/models/llama-v3p1-8b-instruct",
    )
    assert settings.provider == Provider.FIREWORKS
    assert settings.base_url == "https://api.fireworks.ai/inference/v1"
    assert settings.rate_rpm == 0
    assert settings.rate_tpm == 0
