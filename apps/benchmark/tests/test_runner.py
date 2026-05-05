"""Regression tests for `build_settings_for_experiment`.

`base.model_copy(update=...)` does not re-run pydantic validators, so any
field whose value comes from the `apply_provider_defaults` validator stays
at the *base* provider's value unless the runner re-overrides it manually.
That bites every PROVIDER_DEFAULTS field — `base_url` already had an
explicit override; `request_timeout_s` (added later) needed one too.
"""

from __future__ import annotations

import pytest

from benchmark.runner import ExperimentSpec, build_settings_for_experiment


@pytest.fixture
def env_provider_unset(monkeypatch):
    """Simulate the .env.example-recommended groq setup: LLM_PROVIDER unset.

    With provider unset, Settings() defaults to ollama and applies ollama's
    PROVIDER_DEFAULTS — including a 300s request timeout. A groq experiment
    must not inherit that.
    """
    monkeypatch.delenv("LLM_PROVIDER", raising=False)
    monkeypatch.delenv("LLM_BASE_URL", raising=False)
    monkeypatch.delenv("LLM_REQUEST_TIMEOUT_S", raising=False)
    monkeypatch.setenv("LLM_API_KEY", "gsk_test")


def _spec(provider: str, model: str = "llama-3.1-8b-instant") -> ExperimentSpec:
    return ExperimentSpec(
        id="test",
        provider=provider,
        model=model,
        temperature=0.0,
        prompt="zero_shot",
    )


def test_groq_experiment_uses_groq_timeout_even_when_env_provider_is_unset(
    env_provider_unset,
):
    settings = build_settings_for_experiment(_spec("groq"))
    assert settings.request_timeout_s == 60.0
    assert settings.base_url == "https://api.groq.com/openai/v1"


def test_ollama_experiment_uses_ollama_timeout(env_provider_unset):
    settings = build_settings_for_experiment(_spec("ollama", model="llama3.1:8b"))
    assert settings.request_timeout_s == 300.0
