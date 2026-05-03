from enum import StrEnum

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Per-provider defaults. Rate-limit values are intentionally a bit below each
# provider's published quota to leave headroom for clock skew and retries.
# An RPM or TPM of 0 disables that bucket (used for self-hosted backends).
PROVIDER_DEFAULTS: dict[str, dict[str, object]] = {
    "ollama": {
        "base_url": "http://localhost:11434/v1",
        "api_key": "ollama",
        "model": "llama3.1:8b",
        "rate_rpm": 0,
        "rate_tpm": 0,
    },
    "runpod": {
        "model": "meta-llama/Llama-3.1-8B-Instruct",
        "rate_rpm": 0,
        "rate_tpm": 0,
    },
    "groq": {
        "base_url": "https://api.groq.com/openai/v1",
        "model": "llama-3.1-8b-instant",
        "rate_rpm": 28,
        "rate_tpm": 5800,
    },
}


class Provider(StrEnum):
    OLLAMA = "ollama"
    RUNPOD = "runpod"
    GROQ = "groq"


class Settings(BaseSettings):
    # extra="ignore": tolerate non-LLM keys in shared .env files (e.g. when the
    # benchmark harness runs from apps/benchmark/ with its own env vars in the
    # same file). Without this pydantic raises on every unknown key.
    model_config = SettingsConfigDict(env_file=".env", env_prefix="LLM_", extra="ignore")

    # Provider
    provider: Provider = Provider.OLLAMA

    # LLM connection
    base_url: str = ""
    api_key: str = ""
    model: str = ""

    # Service
    host: str = "0.0.0.0"
    port: int = 8001
    log_level: str = "info"

    # LLM parameters
    temperature: float = 0.1
    max_tokens: int = 512

    # Preprocessing
    max_text_length: int = 2048

    # Concurrency and rate limiting
    max_concurrency: int = 3
    rate_rpm: int = 0
    rate_tpm: int = 0
    rate_max_retries: int = 5
    rate_initial_backoff_s: float = 5.0
    rate_max_backoff_s: float = 60.0
    # Floor and padding applied to any pause triggered by a 429. Groq's
    # Retry-After reports the refill time for a single request, not a full
    # bucket reset; respecting it literally causes queued workers to retry in
    # lockstep and hit 429 again. The floor guarantees a useful pause, the
    # padding absorbs clock skew.
    rate_min_pause_s: float = 5.0
    rate_pause_padding: float = 1.25

    # Ask the provider for its live limits at startup (via x-ratelimit-* headers)
    # instead of trusting the hardcoded per-provider defaults. Ollama/RunPod
    # self-hosted backends don't publish limits, so the probe is a no-op there.
    auto_discover_limits: bool = True
    rate_safety_margin: float = 0.9

    @model_validator(mode="before")
    @classmethod
    def apply_provider_defaults(cls, values: dict) -> dict:
        provider = values.get("provider", values.get("LLM_PROVIDER", "ollama")).lower()
        defaults = PROVIDER_DEFAULTS.get(provider, {})
        for field, default in defaults.items():
            current = values.get(field)
            # Treat missing keys and empty strings as "unset" so provider
            # defaults apply — but keep explicit 0 (meaningful for the
            # numeric rate_rpm / rate_tpm fields) out of this branch.
            if current is None or current == "":
                values[field] = default
        return values
