from enum import StrEnum
from typing import Literal

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
        # Local generations on a large model on consumer hardware can run
        # well past 60s; lift the read timeout for self-hosted backends.
        "request_timeout_s": 300.0,
    },
    "runpod": {
        "model": "meta-llama/Llama-3.1-8B-Instruct",
        "rate_rpm": 0,
        "rate_tpm": 0,
        # vLLM continuous batching handles tens of in-flight requests per
        # worker; the global default of 3 leaves throughput on the floor.
        "max_concurrency": 32,
        # RunPod serverless cold-starts can spend 30-90s spinning up a worker
        # before the first token; 60s caused spurious APITimeoutError.
        "request_timeout_s": 600.0,
    },
    "groq": {
        "base_url": "https://api.groq.com/openai/v1",
        "model": "llama-3.1-8b-instant",
        "rate_rpm": 28,
        "rate_tpm": 5800,
        # Explicit so the benchmark runner's per-experiment override picks it
        # up via PROVIDER_DEFAULTS rather than relying on the field default.
        # Without this, `model_copy(update={"provider": "groq"})` from a
        # base built with `LLM_PROVIDER` unset (i.e. ollama=300s) would carry
        # ollama's 300s read timeout into groq calls.
        "request_timeout_s": 60.0,
    },
    # Fireworks does not publish x-ratelimit-* headers; auto-discovery is a
    # no-op there. Caller sets LLM_MODEL explicitly with a namespaced slug
    # like "accounts/fireworks/models/llama-v3p1-8b-instruct".
    "fireworks": {
        "base_url": "https://api.fireworks.ai/inference/v1",
        "rate_rpm": 0,
        "rate_tpm": 0,
    },
}


class Provider(StrEnum):
    OLLAMA = "ollama"
    RUNPOD = "runpod"
    GROQ = "groq"
    FIREWORKS = "fireworks"


# Groq models that support `response_format={"type":"json_schema","strict":true}`.
# Per Groq docs, only the GPT-OSS family enforces the schema at decoding time.
# https://console.groq.com/docs/structured-outputs
GROQ_STRICT_SCHEMA_MODELS: frozenset[str] = frozenset({
    "openai/gpt-oss-20b",
    "openai/gpt-oss-120b",
})

# Groq models that accept `json_schema` with `strict=false` (best-effort).
# Llama 4 Scout is on the published list. Best-effort gives no guarantee but
# nudges harder than plain `json_object`; the coercion layer is the safety net.
GROQ_BEST_EFFORT_SCHEMA_MODELS: frozenset[str] = frozenset({
    "openai/gpt-oss-20b",
    "openai/gpt-oss-120b",
    "openai/gpt-oss-safeguard-20b",
    "meta-llama/llama-4-scout-17b-16e-instruct",
})


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
    # Reasoning models (gpt-oss, o-series) accept this top-level chat-completions
    # field to bound how much chain-of-thought they emit before the answer.
    # Forwarded via `extra_body` so the OpenAI SDK passes it through verbatim;
    # backends that don't recognise it ignore it. None = leave unset.
    reasoning_effort: str | None = None

    # Preprocessing
    max_text_length: int = 2048

    # HTTP timeouts for outbound LLM calls. The OpenAI SDK's default is much
    # longer than 60s, but we own the httpx client so we set these explicitly.
    # Self-hosted backends (Ollama, RunPod) override request_timeout_s upward
    # via PROVIDER_DEFAULTS — see comments there.
    request_timeout_s: float = 60.0
    connect_timeout_s: float = 10.0

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

    # Enum coercion. The `mode='before'` validators on SentimentResponse.emotion
    # and .aspect snap free-form LLM output to a valid enum member. "synonyms"
    # uses a hand-curated table only (no model deps). "embedding" additionally
    # falls back to SBERT nearest-neighbour for novel words — opt-in because
    # it pulls sentence-transformers + a one-time model download. "off" reverts
    # to strict pydantic enum validation (out-of-set output → ValidationError →
    # FailedResult).
    enum_coercion_backend: Literal["off", "synonyms", "embedding"] = "synonyms"
    enum_coercion_embedding_threshold: float = 0.45

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
