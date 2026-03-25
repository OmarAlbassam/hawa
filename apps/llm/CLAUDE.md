# Hawa LLM Service

FastAPI service that performs sentiment analysis on social media posts using vLLM (OpenAI-compatible API).

## Setup & Run

- **Python:** 3.12+
- **Install:** `pip install -r requirements.txt`
- **Configure:** Copy `.env.example` to `.env`, set `LLM_VLLM_BASE_URL` to your vLLM endpoint
- **Run:** `uvicorn main:app --reload --port 8001`
- **Test:** `pytest`

## Architecture

- Stateless HTTP service — no database access
- Receives post text from Spring Boot backend, returns structured sentiment analysis
- Talks to vLLM (local or RunPod) via the OpenAI Python SDK
- Same code works for local vLLM and RunPod — just change `LLM_VLLM_BASE_URL`

## Project Structure

```
├── main.py                    # FastAPI app + lifespan
├── config.py                  # pydantic-settings (env vars)
├── models.py                  # Pydantic request/response schemas
├── routes/
│   └── analyze.py             # Thin HTTP handlers
├── services/
│   ├── analyzer.py            # Core orchestration (preprocess → LLM → validate)
│   └── llm_client.py          # OpenAI SDK wrapper
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
| GET    | /health          | Health check + vLLM connectivity |
| POST   | /analyze         | Analyze single post    |
| POST   | /analyze/batch   | Analyze multiple posts (accepts brand context) |

## Configuration

All env vars are prefixed with `LLM_`. See `.env.example` for the full list.

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_VLLM_BASE_URL` | `http://localhost:8000/v1` | vLLM endpoint |
| `LLM_VLLM_API_KEY` | `EMPTY` | API key for vLLM |
| `LLM_VLLM_MODEL` | `meta-llama/Llama-3.1-8B-Instruct` | Model name |
| `LLM_PORT` | `8001` | Service port |
| `LLM_TEMPERATURE` | `0.1` | LLM temperature |
| `LLM_MAX_TOKENS` | `512` | Max response tokens |
| `LLM_MAX_TEXT_LENGTH` | `2048` | Max post text length before truncation |
