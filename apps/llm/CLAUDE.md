# Hawa LLM Service

FastAPI service that performs sentiment analysis on social media posts using an OpenAI-compatible LLM API (Ollama for dev, RunPod for prod).

## Setup & Run

- **Python:** 3.12+
- **Install:** `pip install -r requirements.txt`
- **Configure:** Copy `.env.example` to `.env`, set `LLM_PROVIDER` (`ollama`, `runpod`, or `groq`)
- **Run:** `uvicorn main:app --reload --port 8001`
- **Test:** `pytest`

## Architecture

- Stateless HTTP service — no database access
- Receives post text from Spring Boot backend, returns structured sentiment analysis
- Talks to LLM via the OpenAI Python SDK (both Ollama and RunPod expose OpenAI-compatible APIs)
- Switch providers with `LLM_PROVIDER` env var — defaults to `ollama` for local dev

## Project Structure

```
├── main.py                    # FastAPI app + lifespan
├── config.py                  # pydantic-settings (env vars)
├── models.py                  # Pydantic request/response schemas
├── routes/
│   └── analyze.py             # Thin HTTP handlers
├── services/
│   ├── analyzer.py            # Core orchestration (preprocess → LLM → validate)
│   ├── llm_client.py          # OpenAI SDK wrapper + 429 retry loop
│   └── rate_limiter.py        # Provider-agnostic RPM/TPM bucket + pause gate
├── prompts/
│   └── sentiment.py           # Prompt templates with brand context injection
├── utils/
│   └── preprocessing.py       # Text cleaning, URL removal, truncation
└── tests/
    ├── test_analyzer.py
    └── test_preprocessing.py
```

## Conventions

- **Routes are thin** — HTTP plumbing only, delegate to services
- **Services hold logic** — analysis orchestration, retries, normalization
- **Prompts are templated** — accept brand context (name, industry, keywords)
- Use async functions throughout (FastAPI + AsyncOpenAI)
- Environment config via pydantic-settings, all prefixed with `LLM_`
- Type hints on all function signatures
- All request/response models in `models.py`

## Endpoints

| Method | Path             | Description            |
|--------|------------------|------------------------|
| GET    | /health          | Health check + LLM connectivity |
| POST   | /analyze         | Analyze single post    |
| POST   | /analyze/batch   | Analyze multiple posts (accepts brand context) |

## Configuration

All env vars are prefixed with `LLM_`. See `.env.example` for the full list.

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_PROVIDER` | `ollama` | LLM provider: `ollama` (dev), `runpod` (prod), `groq`, or `fireworks` |
| `LLM_BASE_URL` | per provider | LLM endpoint (ollama: `http://localhost:11434/v1`, fireworks: `https://api.fireworks.ai/inference/v1`) |
| `LLM_API_KEY` | per provider | API key (ollama: `ollama`, runpod/groq/fireworks: required) |
| `LLM_MODEL` | per provider | Model name (ollama: `llama3.1:8b`, groq: `llama-3.1-8b-instant`, fireworks: namespaced slug like `accounts/fireworks/models/llama-v3p1-8b-instruct`) |
| `LLM_PORT` | `8001` | Service port |
| `LLM_TEMPERATURE` | `0.1` | LLM temperature |
| `LLM_MAX_TOKENS` | `512` | Max response tokens |
| `LLM_MAX_TEXT_LENGTH` | `2048` | Max post text length before truncation |
| `LLM_MAX_CONCURRENCY` | `3` | Max concurrent outbound LLM calls (safety net on top of the limiter) |
| `LLM_RATE_RPM` | per provider | Requests-per-minute budget. `0` disables. Groq default: `28` |
| `LLM_RATE_TPM` | per provider | Tokens-per-minute budget. `0` disables. Groq default: `5800` |
| `LLM_RATE_MAX_RETRIES` | `5` | Attempts per request when 429s are encountered |
| `LLM_RATE_INITIAL_BACKOFF_S` | `5.0` | Backoff seconds when Retry-After header is absent |
| `LLM_RATE_MAX_BACKOFF_S` | `60.0` | Cap on the computed backoff fallback |
| `LLM_RATE_MIN_PAUSE_S` | `5.0` | Floor on the pause applied after any 429. Groq's `Retry-After` reports the time until a single request can retry, not a full bucket reset — without a floor, queued workers resume in lockstep and re-trigger the limit. |
| `LLM_RATE_PAUSE_PADDING` | `1.25` | Multiplier applied to the server-provided `Retry-After` before it reaches the pause gate. Absorbs clock skew and stampede effects. |
| `LLM_AUTO_DISCOVER_LIMITS` | `true` | At startup, read `x-ratelimit-*` headers and override the configured RPM/TPM. Falls back to the static defaults on any failure. |
| `LLM_RATE_SAFETY_MARGIN` | `0.9` | Fraction of the discovered limit we actually use (leaves headroom for clock skew / other clients on the same key). |

## Rate Limiting

All outbound LLM calls pass through a single `ProviderRateLimiter`
(`services/rate_limiter.py`). It enforces RPM/TPM token buckets proactively
so bursts don't exceed the provider's quota, and exposes a shared pause
gate: when any worker sees a 429, it notifies the limiter with
`max(LLM_RATE_MIN_PAUSE_S, Retry-After * LLM_RATE_PAUSE_PADDING)` and all
other workers block on the same gate until it lifts. The floor exists
because providers like Groq return a `Retry-After` sized for a single
request's refill, not a full bucket reset — respecting it literally lets
queued workers resume in lockstep and re-trigger the limit. This replaces the older stacked retry strategy (OpenAI SDK +
instructor + `FailedResult`) that produced noisy 429 log spam.

The limiter sits in front of `LLMClient.analyze()`. Both the OpenAI SDK's
internal retry (`max_retries=0`) and instructor's HTTP retry are disabled
so the wrapper owns retry policy end-to-end. Instructor's validation retry
(`max_retries=1`) stays on — it handles malformed JSON without costing
rate budget.

At startup, `services/limits_probe.py` issues one GET `/models` request and
inspects `x-ratelimit-limit-requests`, `x-ratelimit-limit-tokens`, and the
matching `-reset-*` durations to infer the provider's actual per-minute
caps. Those override the hardcoded `PROVIDER_DEFAULTS` after applying
`LLM_RATE_SAFETY_MARGIN`. Probe failure (Ollama/RunPod/Fireworks, offline,
missing headers) silently falls back to the configured values. Fireworks
in particular does not publish `x-ratelimit-*` headers, so the probe is
effectively a no-op there.

When retries are exhausted the wrapper raises `RateLimitExhaustedError`,
which the analyzer converts into a `FailedResult` with a distinct
`rate_limited:` prefix so operators can distinguish it from generic
failures.
