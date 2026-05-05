# Hawa Benchmark

Research benchmarking harness for evaluating different LLMs, prompts, temperatures, and few-shot strategies on the Hawa sentiment-analysis task. Output feeds the IS498 capstone report.

## Setup & Run

- **Python:** 3.12+
- **Install:** `pip install -e .` (depends on `../llm` as a local source dep)
- **Configure:** copy `.env.example` to `.env`. The benchmark imports the LLM service's `Settings` / `LLMClient` / `AnalyzerService` directly, so it reads the same `LLM_*` env vars.
- **Embed dataset:** `benchmark embed`  (one-time per dataset/model)
- **Run experiments:** `benchmark run configs/<file>.yaml`
- **Browse results:** `streamlit run viewer/app.py`
- **Notebook (error analysis):** `pip install -e ".[notebooks]"` then `jupyter lab notebooks/error_analysis.ipynb`
- **Test:** `pytest`

## Architecture

- **Standalone app** under `apps/benchmark/`. Imports `apps/llm/` as a library — same prompts, preprocessing, rate limiter, and retry logic as production.
- **Stateless runner** that reads a YAML experiment matrix, expands it, dedupes via sqlite cache, executes, and writes one parquet per experiment.
- **Embedding-based few-shot** — `sentence-transformers` produces a (N, dim) numpy array stored in `.npz`. Retrieval is in-memory cosine similarity. No vector DB at this scale (~100s of samples).
- **k-fold leakage prevention** — when retrieving few-shot exemplars for a test post, the post and any sample sharing its fold are masked out of the index. Without this, few-shot results are inflated and the comparison is meaningless.

## Project Structure

```
benchmark/
├── data/
│   └── control.jsonl              # post_id, text, gt_score, gt_emotion, gt_aspect, fold
├── benchmark/
│   ├── runner.py                  # experiment orchestrator
│   ├── cache.py                   # sqlite, keyed on (model, prompt_hash, temp, post_id, fewshot_ids_hash)
│   ├── dataset.py                 # JSONL loader, taxonomies, k-fold splitter
│   ├── retrieval/
│   │   ├── embedder.py            # sentence-transformers wrapper
│   │   └── store.py               # numpy-backed kNN with fold masking
│   ├── prompts/
│   │   ├── zero_shot.py
│   │   ├── few_shot_static.py
│   │   └── few_shot_retrieved.py
│   ├── metrics/
│   │   ├── classification.py      # accuracy, macro-F1, per-class F1, confusion matrix
│   │   ├── regression.py          # MAE, RMSE
│   │   ├── aspect.py              # taxonomy match (post-`Aspect` enum)
│   │   └── significance.py        # McNemar, paired bootstrap CIs
│   ├── report.py                  # parquet + summary plots
│   └── cli.py                     # typer app
├── viewer/
│   └── app.py                     # Streamlit leaderboard + diff browser
├── notebooks/
│   └── error_analysis.ipynb       # starter analyses; runs against parquets in results/
├── configs/
│   └── example.yaml               # experiment matrix
├── tests/
└── results/                       # gitignored parquet outputs
```

## Conventions

- **Reuse, don't fork.** Production prompt + preprocessing live in `apps/llm/`. Import `build_system_prompt`, `clean_text`, `LLMClient`, `AnalyzerService` directly. Don't copy.
- **Determinism for headline numbers.** Run at `temperature=0` for the main table; vary temperature only for variance analysis.
- **Cache everything.** Same (model, prompt_hash, temp, post_id, fewshot_ids_hash) → return cached result. Re-running an interrupted experiment must skip done cells.
- **Hash the prompt template, dataset, and code version into every result row.** Without this, a paper-claim row can't be reproduced six months later.
- **No paid APIs in CI.** Tests use a fake `LLMClient` that returns canned `SentimentResponse`s.
- **Aspect taxonomy follows the production `Aspect` enum** (`apps/llm/models.py`). If you extend it, extend it there first; the benchmark imports the enum.

## Rate Limiting

The benchmark reuses `ProviderRateLimiter` from `apps/llm/services/rate_limiter.py`. Per-experiment overrides:

- `rate_rpm`, `rate_tpm` — explicit budget for the experiment (overrides env defaults)
- `max_concurrency` — outbound concurrency cap
- `wall_clock_budget_s` — hard timeout that aborts the experiment cleanly (RunPod safety)

The runner auto-probes per-minute limits via `x-ratelimit-*` response
headers for `groq` only. `ollama`, `runpod`, and `fireworks` skip the
probe — the first two are self-hosted and the third doesn't publish
those headers — so set `rate_rpm` / `rate_tpm` explicitly in the YAML
when you need a tighter ceiling than the provider's default.

## Reproducibility Checklist

Every result row must record:
- exact model id (Groq returns build hash in headers — capture it)
- prompt template hash
- dataset hash + fold assignment
- temperature, max_tokens, seed (where supported)
- code git sha
- timestamp
