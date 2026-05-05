"""Experiment runner.

Reads a YAML experiment matrix, expands it, runs each experiment against the
control dataset, and writes one parquet file of per-post predictions per
experiment plus a summary metrics row.

Reuses production components directly:
- `LLMClient` from `apps/llm/services/llm_client.py` (rate limiter + retry)
- `ProviderRateLimiter` from `apps/llm/services/rate_limiter.py`
- `clean_text` from `apps/llm/utils/preprocessing.py`
- `build_system_prompt` from `apps/llm/prompts/sentiment.py`
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from itertools import product
from pathlib import Path
from typing import Any

import pandas as pd
import yaml
from rich.console import Console
from rich.progress import (
    BarColumn,
    MofNCompleteColumn,
    Progress,
    SpinnerColumn,
    TextColumn,
    TimeElapsedColumn,
    TimeRemainingColumn,
)

from openai import AsyncOpenAI

from config import PROVIDER_DEFAULTS, Settings
from models import IrrelevanceReason, SentimentResponse
from services.limits_probe import DiscoveredLimits, discover_limits
from services.llm_client import LLMClient, RateLimitExhaustedError, TokenUsage
from services.rate_limiter import ProviderRateLimiter
from utils.preprocessing import clean_text

from benchmark.cache import (
    CacheKey,
    CachedResult,
    ResultCache,
    hash_fewshot_ids,
    hash_prompt,
)
from benchmark.dataset import Sample, assign_folds, dataset_hash, load_dataset
from benchmark.metrics.aspect import normalize_aspect
from benchmark.prompts import few_shot_retrieved, few_shot_static, zero_shot
from benchmark.retrieval.embedder import SBERTEmbedder
from benchmark.retrieval.store import KNNStore

logger = logging.getLogger(__name__)


@dataclass
class ExperimentSpec:
    """One concrete experiment cell (post-matrix-expansion)."""

    id: str
    provider: str
    model: str
    temperature: float
    prompt: str  # "zero_shot" | "few_shot_static" | "few_shot_retrieved"
    fewshot_k: int = 0
    fewshot_static_ids: list[int] = field(default_factory=list)
    embedder_model: str = "BAAI/bge-small-en-v1.5"
    rate_rpm: int | None = None
    rate_tpm: int | None = None
    max_concurrency: int = 3
    wall_clock_budget_s: float | None = None
    brand_name: str | None = None
    brand_industry: str | None = None
    keywords: list[str] | None = None
    # Per-experiment LLM-parameter overrides. None = inherit Settings default.
    # Reasoning models (gpt-oss, o-series) need both: a higher token budget so
    # the JSON answer fits after the chain-of-thought, and `reasoning_effort:
    # "low"` to keep the chain-of-thought short in the first place.
    max_tokens: int | None = None
    reasoning_effort: str | None = None
    # Optional held-out exemplar pool. When set, few-shot exemplars are drawn
    # from this JSONL instead of the eval dataset — closes static self-leakage
    # and the retrieved fold-mate concern. `exemplar_embeddings` is only needed
    # for `prompt: few_shot_retrieved` (kNN over the pool); static just needs
    # the JSONL to resolve `fewshot_static_ids`.
    exemplar_dataset: str | None = None
    exemplar_embeddings: str | None = None


@dataclass
class PerPostResult:
    experiment_id: str
    post_id: int
    fold: int
    gt_score: float
    gt_emotion: str
    gt_aspect: str
    pred_relevant: bool | None
    pred_score: float | None
    pred_emotion: str | None
    pred_aspect_raw: str | None
    pred_aspect_normalized: str | None
    error: str | None
    latency_ms: float | None
    fewshot_post_ids: list[int]
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    cached_tokens: int | None = None


def load_config(path: str | Path) -> dict[str, Any]:
    with open(path) as f:
        return yaml.safe_load(f)


def expand_matrix(config: dict[str, Any]) -> list[ExperimentSpec]:
    """Expand a YAML matrix into concrete `ExperimentSpec`s.

    Top-level `experiments` is a list. Each entry can have list-valued fields
    for `temperature`, `model`, etc., which are cross-producted. The `id` is
    suffixed with the varying fields to keep cells distinct.
    """
    out: list[ExperimentSpec] = []
    for entry in config.get("experiments", []):
        base_id = entry["id"]
        provider = entry.get("provider", "groq")
        prompt = entry.get("prompt", "zero_shot")

        models = _as_list(entry.get("model"))
        temps = _as_list(entry.get("temperature", 0.0))
        ks = _as_list(entry.get("fewshot_k", 0))

        for model, temp, k in product(models, temps, ks):
            suffix_parts: list[str] = []
            if len(models) > 1:
                suffix_parts.append(_safe_id(model))
            if len(temps) > 1:
                suffix_parts.append(f"t{temp}")
            if len(ks) > 1:
                suffix_parts.append(f"k{k}")
            cell_id = base_id if not suffix_parts else f"{base_id}__{'_'.join(suffix_parts)}"

            out.append(
                ExperimentSpec(
                    id=cell_id,
                    provider=provider,
                    model=model,
                    temperature=float(temp),
                    prompt=prompt,
                    fewshot_k=int(k),
                    fewshot_static_ids=entry.get("fewshot_static_ids", []) or [],
                    embedder_model=entry.get("embedder_model", "BAAI/bge-small-en-v1.5"),
                    rate_rpm=entry.get("rate_rpm"),
                    rate_tpm=entry.get("rate_tpm"),
                    max_concurrency=int(entry.get("max_concurrency", 3)),
                    wall_clock_budget_s=entry.get("wall_clock_budget_s"),
                    brand_name=entry.get("brand_name"),
                    brand_industry=entry.get("brand_industry"),
                    keywords=entry.get("keywords"),
                    max_tokens=entry.get("max_tokens"),
                    reasoning_effort=entry.get("reasoning_effort"),
                    exemplar_dataset=entry.get("exemplar_dataset"),
                    exemplar_embeddings=entry.get("exemplar_embeddings"),
                )
            )
    return out


def _as_list(v: Any) -> list[Any]:
    if v is None:
        return [None]
    return v if isinstance(v, list) else [v]


def _safe_id(s: str) -> str:
    return "".join(c if c.isalnum() else "-" for c in s)[:32]


def build_settings_for_experiment(spec: ExperimentSpec) -> Settings:
    """Build a `Settings` object scoped to one experiment.

    Inherits provider defaults, then layers the experiment's overrides.
    Reads `LLM_API_KEY` from env via `Settings`. The rate-limit fields
    (`rate_rpm`/`rate_tpm`) only flow through when the YAML explicitly sets
    them — that's the manual escape hatch. The default path is auto-probing
    in `apply_discovered_limits` (called from `run_all`), which discovers
    Groq's actual limits via response headers.
    """
    base = Settings()  # picks up env vars
    overrides: dict[str, Any] = {
        "provider": spec.provider,
        "model": spec.model,
        "temperature": spec.temperature,
        "max_concurrency": spec.max_concurrency,
    }
    defaults = PROVIDER_DEFAULTS.get(spec.provider, {})
    if "base_url" in defaults:
        overrides["base_url"] = defaults["base_url"]
    # `model_copy(update=...)` doesn't re-run `apply_provider_defaults`, so
    # the timeout that the validator picked at `Settings()` time (driven by
    # env's LLM_PROVIDER) leaks into the experiment's provider unless we
    # re-apply it here. Same shape as the base_url override above.
    if "request_timeout_s" in defaults:
        overrides["request_timeout_s"] = defaults["request_timeout_s"]
    if spec.rate_rpm is not None:
        overrides["rate_rpm"] = spec.rate_rpm
    if spec.rate_tpm is not None:
        overrides["rate_tpm"] = spec.rate_tpm
    if spec.max_tokens is not None:
        overrides["max_tokens"] = spec.max_tokens
    if spec.reasoning_effort is not None:
        overrides["reasoning_effort"] = spec.reasoning_effort
    return base.model_copy(update=overrides)


async def apply_discovered_limits(specs: list[ExperimentSpec]) -> None:
    """Probe each unique (provider, model) once and apply the result to specs.

    Mutates `spec.rate_rpm` / `spec.rate_tpm` in place when:
    - they aren't already set in the YAML (explicit YAML wins, always)
    - the provider supports the probe (skip Ollama; it's self-hosted)
    - the probe actually returned headers

    On any failure (network, missing headers, daily-rather-than-per-minute
    bucket reported) the spec values stay None and downstream `Settings()`
    fall back to env vars / `PROVIDER_DEFAULTS`. So losing the probe never
    leaves the limiter unconfigured — only un-tightened.
    """
    settings = Settings()
    margin = settings.rate_safety_margin

    seen: dict[tuple[str, str], DiscoveredLimits] = {}
    for spec in specs:
        # Self-hosted backends (Ollama, RunPod serverless vLLM) don't publish
        # x-ratelimit-* headers. The probe wakes a cold RunPod worker for
        # nothing — skip it. Fireworks doesn't publish them either, so the
        # probe burns a request for no signal. Concurrency on these
        # backends is bounded by worker capacity or account-wide caps, not
        # per-minute headers we can read off a response.
        if spec.provider in ("ollama", "runpod", "fireworks"):
            continue
        if spec.rate_rpm is not None and spec.rate_tpm is not None:
            continue  # both manually overridden — no probe needed

        key = (spec.provider, spec.model)
        discovered = seen.get(key)
        if discovered is None:
            discovered = await _probe(spec)
            seen[key] = discovered

        if discovered.rpm is not None and spec.rate_rpm is None:
            spec.rate_rpm = int(discovered.rpm * margin)
        if discovered.tpm is not None and spec.rate_tpm is None:
            spec.rate_tpm = int(discovered.tpm * margin)


async def _probe(spec: ExperimentSpec) -> DiscoveredLimits:
    """Open a one-shot OpenAI client just for the rate-limit probe."""
    settings = build_settings_for_experiment(spec)
    client = AsyncOpenAI(
        base_url=settings.base_url,
        api_key=settings.api_key,
        max_retries=0,
    )
    try:
        return await discover_limits(client, settings.model)
    except Exception as e:  # pragma: no cover — defensive; probe is best-effort
        logger.info("rate-limit probe for %s/%s failed: %s", spec.provider, spec.model, e)
        return DiscoveredLimits()
    finally:
        await client.close()


async def run_experiment(
    spec: ExperimentSpec,
    samples: list[Sample],
    cache: ResultCache,
    store: KNNStore | None,
    embedder: SBERTEmbedder | None,
    *,
    exemplar_samples: list[Sample] | None = None,
    exemplar_store: KNNStore | None = None,
    on_post_done: Callable[[PerPostResult], None] | None = None,
) -> list[PerPostResult]:
    """Run one experiment over the full sample list.

    `on_post_done` fires inside the results lock as each post finishes, so a
    progress display in the caller can update with a consistent count. The
    callback is intentionally untyped beyond the result so the runner has no
    dependency on rich/streamlit/etc.

    `exemplar_samples` / `exemplar_store` (when set) come from a held-out
    exemplar JSONL/npz pair and replace the eval dataset as the few-shot pool.
    """
    settings = build_settings_for_experiment(spec)
    rate_limiter = ProviderRateLimiter(rpm=settings.rate_rpm, tpm=settings.rate_tpm)
    client = LLMClient(settings, rate_limiter)

    try:
        semaphore = asyncio.Semaphore(spec.max_concurrency)
        deadline = (
            time.monotonic() + spec.wall_clock_budget_s
            if spec.wall_clock_budget_s
            else None
        )
        results: list[PerPostResult] = []
        results_lock = asyncio.Lock()

        async def _process(sample: Sample) -> None:
            async with semaphore:
                if deadline is not None and time.monotonic() > deadline:
                    result = _make_error_result(spec, sample, "wall_clock_exceeded", [])
                else:
                    result = await _process_one(
                        spec, sample, settings, client, cache, store, embedder,
                        exemplar_samples=exemplar_samples,
                        exemplar_store=exemplar_store,
                    )
                async with results_lock:
                    results.append(result)
                    if on_post_done is not None:
                        on_post_done(result)

        await asyncio.gather(*(_process(s) for s in samples))
        return results
    finally:
        # LLMClient owns an httpx.AsyncClient; long benchmark runs that skip
        # this leak connection pools and emit unclosed-client warnings.
        await client.aclose()


async def _process_one(
    spec: ExperimentSpec,
    sample: Sample,
    settings: Settings,
    client: LLMClient,
    cache: ResultCache,
    store: KNNStore | None,
    embedder: SBERTEmbedder | None,
    *,
    exemplar_samples: list[Sample] | None = None,
    exemplar_store: KNNStore | None = None,
) -> PerPostResult:
    cleaned = clean_text(sample.text, settings.max_text_length)
    if not cleaned:
        return _make_irrelevant_result(spec, sample, IrrelevanceReason.EMPTY, [])

    prompt, fewshot_ids = _build_prompt(
        spec, sample, store, embedder,
        exemplar_samples=exemplar_samples,
        exemplar_store=exemplar_store,
    )
    p_hash = hash_prompt(prompt)
    fs_hash = hash_fewshot_ids(fewshot_ids)
    key = CacheKey(
        experiment_id=spec.id,
        model=spec.model,
        prompt_hash=p_hash,
        temperature=spec.temperature,
        post_id=sample.post_id,
        fewshot_hash=fs_hash,
    )

    cached = cache.get(key)
    if cached is not None:
        return _from_cached(spec, sample, cached, fewshot_ids)

    started = time.perf_counter()
    try:
        # spec.id pins every post in this experiment to the same replica via
        # the x-session-affinity header. On Fireworks/Groq this turns the
        # static system prompt into a cache hit after the first call.
        response, usage = await client.analyze_with_usage(
            prompt, cleaned, session_id=spec.id
        )
        latency_ms = (time.perf_counter() - started) * 1000.0
    except RateLimitExhaustedError as e:
        cache.put(key, CachedResult(None, None, None, None, None, f"rate_limited: {e}", None))
        return _make_error_result(spec, sample, f"rate_limited: {e}", fewshot_ids)
    except Exception as e:
        latency_ms = (time.perf_counter() - started) * 1000.0
        cache.put(key, CachedResult(None, None, None, None, None, str(e), latency_ms))
        return _make_error_result(spec, sample, str(e), fewshot_ids)

    result = _from_response(spec, sample, response, latency_ms, fewshot_ids, usage)
    cache.put(
        key,
        CachedResult(
            is_relevant=response.is_relevant,
            irrelevance_reason=response.irrelevance_reason.value if response.irrelevance_reason else None,
            pred_score=response.score if response.is_relevant else None,
            pred_emotion=response.emotion.value if response.is_relevant else None,
            pred_aspect=response.aspect if response.is_relevant else None,
            error=None,
            latency_ms=latency_ms,
            prompt_tokens=usage.prompt_tokens if usage else None,
            completion_tokens=usage.completion_tokens if usage else None,
            total_tokens=usage.total_tokens if usage else None,
            cached_tokens=usage.cached_tokens if usage else None,
        ),
    )
    return result


def _build_prompt(
    spec: ExperimentSpec,
    sample: Sample,
    store: KNNStore | None,
    embedder: SBERTEmbedder | None,
    exemplar_samples: list[Sample] | None = None,
    exemplar_store: KNNStore | None = None,
) -> tuple[str, list[int]]:
    if spec.prompt == "zero_shot":
        return (
            zero_shot.build(
                sample,
                brand_name=spec.brand_name,
                brand_industry=spec.brand_industry,
                keywords=spec.keywords,
            ),
            [],
        )
    if spec.prompt == "few_shot_static":
        # Prefer the held-out exemplar pool when set; this closes static
        # self-leakage (fewshot_static_ids can never resolve to the test post).
        if exemplar_samples is not None:
            pool_samples = exemplar_samples
        elif store is not None:
            pool_samples = store.samples
        else:
            raise RuntimeError(
                "few_shot_static needs an exemplar pool — either an "
                "`exemplar_dataset` on the experiment or the eval dataset's KNN store"
            )
        by_id = {s.post_id: s for s in pool_samples}
        exemplars = [by_id[i] for i in spec.fewshot_static_ids if i in by_id]
        return (
            few_shot_static.build(
                sample,
                exemplars=exemplars,
                brand_name=spec.brand_name,
                brand_industry=spec.brand_industry,
                keywords=spec.keywords,
            ),
            spec.fewshot_static_ids,
        )
    if spec.prompt == "few_shot_retrieved":
        if embedder is None:
            raise RuntimeError("few_shot_retrieved needs an embedder")
        # When a held-out exemplar store is provided, query it instead of the
        # eval store and drop fold masking — folds in the exemplar file are
        # meaningless relative to the eval k-fold split. `exclude_post_ids`
        # stays as a defensive no-op against accidental id collisions.
        if exemplar_store is not None:
            return few_shot_retrieved.build(
                sample,
                store=exemplar_store,
                embedder=embedder,
                k=spec.fewshot_k,
                brand_name=spec.brand_name,
                brand_industry=spec.brand_industry,
                keywords=spec.keywords,
                exclude_folds=set(),
            )
        if store is None:
            raise RuntimeError("few_shot_retrieved needs an embedder + KNN store")
        return few_shot_retrieved.build(
            sample,
            store=store,
            embedder=embedder,
            k=spec.fewshot_k,
            brand_name=spec.brand_name,
            brand_industry=spec.brand_industry,
            keywords=spec.keywords,
        )
    raise ValueError(f"unknown prompt variant: {spec.prompt!r}")


def _make_irrelevant_result(
    spec: ExperimentSpec,
    sample: Sample,
    reason: IrrelevanceReason,
    fewshot_ids: list[int],
) -> PerPostResult:
    return PerPostResult(
        experiment_id=spec.id,
        post_id=sample.post_id,
        fold=sample.fold,
        gt_score=sample.gt.score,
        gt_emotion=sample.gt.emotion,
        gt_aspect=sample.gt.aspect,
        pred_relevant=False,
        pred_score=None,
        pred_emotion=None,
        pred_aspect_raw=None,
        pred_aspect_normalized=None,
        error=f"irrelevant: {reason.value}",
        latency_ms=None,
        fewshot_post_ids=fewshot_ids,
    )


def _make_error_result(
    spec: ExperimentSpec, sample: Sample, error: str, fewshot_ids: list[int]
) -> PerPostResult:
    return PerPostResult(
        experiment_id=spec.id,
        post_id=sample.post_id,
        fold=sample.fold,
        gt_score=sample.gt.score,
        gt_emotion=sample.gt.emotion,
        gt_aspect=sample.gt.aspect,
        pred_relevant=None,
        pred_score=None,
        pred_emotion=None,
        pred_aspect_raw=None,
        pred_aspect_normalized=None,
        error=error,
        latency_ms=None,
        fewshot_post_ids=fewshot_ids,
    )


def _from_response(
    spec: ExperimentSpec,
    sample: Sample,
    response: SentimentResponse,
    latency_ms: float,
    fewshot_ids: list[int],
    usage: TokenUsage | None,
) -> PerPostResult:
    pt = usage.prompt_tokens if usage else None
    ct = usage.completion_tokens if usage else None
    tt = usage.total_tokens if usage else None
    cached = usage.cached_tokens if usage else None
    if not response.is_relevant:
        return PerPostResult(
            experiment_id=spec.id,
            post_id=sample.post_id,
            fold=sample.fold,
            gt_score=sample.gt.score,
            gt_emotion=sample.gt.emotion,
            gt_aspect=sample.gt.aspect,
            pred_relevant=False,
            pred_score=None,
            pred_emotion=None,
            pred_aspect_raw=None,
            pred_aspect_normalized=None,
            error=None,
            latency_ms=latency_ms,
            fewshot_post_ids=fewshot_ids,
            prompt_tokens=pt,
            completion_tokens=ct,
            total_tokens=tt,
            cached_tokens=cached,
        )
    return PerPostResult(
        experiment_id=spec.id,
        post_id=sample.post_id,
        fold=sample.fold,
        gt_score=sample.gt.score,
        gt_emotion=sample.gt.emotion,
        gt_aspect=sample.gt.aspect,
        pred_relevant=True,
        pred_score=response.score,
        pred_emotion=response.emotion.value,
        pred_aspect_raw=response.aspect,
        pred_aspect_normalized=normalize_aspect(response.aspect),
        error=None,
        latency_ms=latency_ms,
        fewshot_post_ids=fewshot_ids,
        prompt_tokens=pt,
        completion_tokens=ct,
        total_tokens=tt,
        cached_tokens=cached,
    )


def _from_cached(
    spec: ExperimentSpec,
    sample: Sample,
    cached: CachedResult,
    fewshot_ids: list[int],
) -> PerPostResult:
    if cached.error:
        return _make_error_result(spec, sample, cached.error, fewshot_ids)
    return PerPostResult(
        experiment_id=spec.id,
        post_id=sample.post_id,
        fold=sample.fold,
        gt_score=sample.gt.score,
        gt_emotion=sample.gt.emotion,
        gt_aspect=sample.gt.aspect,
        pred_relevant=cached.is_relevant,
        pred_score=cached.pred_score,
        pred_emotion=cached.pred_emotion,
        pred_aspect_raw=cached.pred_aspect,
        pred_aspect_normalized=normalize_aspect(cached.pred_aspect)
        if cached.pred_aspect is not None
        else None,
        error=None,
        latency_ms=cached.latency_ms,
        fewshot_post_ids=fewshot_ids,
        prompt_tokens=cached.prompt_tokens,
        completion_tokens=cached.completion_tokens,
        total_tokens=cached.total_tokens,
        cached_tokens=cached.cached_tokens,
    )


def _validate_provider_config(specs: list[ExperimentSpec]) -> None:
    """Fail fast on misconfiguration that would otherwise silently misroute calls.

    RunPod's base_url is per-user (each endpoint has a unique id), so it has no
    PROVIDER_DEFAULTS entry. If LLM_BASE_URL isn't set in env, AsyncOpenAI
    silently defaults to api.openai.com. If env points at Ollama but the YAML
    selects runpod, every call fails with connection-refused. Catch both here.

    Fireworks has a fixed base_url from PROVIDER_DEFAULTS, so we only need to
    confirm an api key is present — without it, every call returns 401 and
    burns the experiment budget mid-run.
    """
    if any(s.provider == "runpod" for s in specs):
        settings = build_settings_for_experiment(
            next(s for s in specs if s.provider == "runpod")
        )
        base_url = (settings.base_url or "").rstrip("/")
        if not base_url or "runpod.ai" not in base_url:
            raise RuntimeError(
                "provider=runpod requires LLM_BASE_URL to point at a RunPod endpoint, "
                "e.g. https://api.runpod.ai/v2/<endpoint-id>/openai/v1. "
                f"Got base_url={settings.base_url!r}. Set it in apps/benchmark/.env."
            )
        if not settings.api_key:
            raise RuntimeError(
                "provider=runpod requires LLM_API_KEY to be set in apps/benchmark/.env."
            )

    if any(s.provider == "fireworks" for s in specs):
        settings = build_settings_for_experiment(
            next(s for s in specs if s.provider == "fireworks")
        )
        if not settings.api_key:
            raise RuntimeError(
                "provider=fireworks requires LLM_API_KEY to be set in apps/benchmark/.env."
            )


def _validate_exemplar_config(specs: list[ExperimentSpec]) -> None:
    """Fail fast on misconfigured exemplar-pool fields.

    Catches three cases that would otherwise crash mid-run:
    - `exemplar_dataset` set but the file doesn't exist.
    - `prompt: few_shot_retrieved` with `exemplar_dataset` but no
      `exemplar_embeddings` (the kNN store needs the npz, no fallback).
    - `exemplar_embeddings` set but the npz file doesn't exist.
    """
    for spec in specs:
        if spec.exemplar_dataset is None:
            continue
        ds_path = Path(spec.exemplar_dataset)
        if not ds_path.exists():
            raise RuntimeError(
                f"[{spec.id}] exemplar_dataset {spec.exemplar_dataset!r} does not exist"
            )
        if spec.prompt == "few_shot_retrieved":
            if spec.exemplar_embeddings is None:
                raise RuntimeError(
                    f"[{spec.id}] prompt=few_shot_retrieved with exemplar_dataset "
                    f"requires exemplar_embeddings (path to a .npz). "
                    f"Build it with: benchmark embed --data {spec.exemplar_dataset} "
                    f"--out path/to/exemplars.npz"
                )
            emb_path = Path(spec.exemplar_embeddings)
            if not emb_path.exists():
                raise RuntimeError(
                    f"[{spec.id}] exemplar_embeddings {spec.exemplar_embeddings!r} "
                    f"does not exist — run `benchmark embed --data "
                    f"{spec.exemplar_dataset} --out {spec.exemplar_embeddings}`"
                )


async def run_all(
    config_path: str | Path,
    *,
    data_path: str | Path = "data/control.jsonl",
    embeddings_path: str | Path = "data/embeddings.npz",
    cache_path: str | Path = "cache.db",
    results_dir: str | Path = "results",
    k_folds: int = 5,
    console: Console | None = None,
) -> dict[str, str]:
    """Top-level entry point. Returns experiment_id -> output parquet path."""
    config = load_config(config_path)
    specs = expand_matrix(config)
    _validate_provider_config(specs)
    _validate_exemplar_config(specs)

    samples = load_dataset(data_path)
    samples = assign_folds(samples, k=k_folds)
    ds_hash = dataset_hash(samples)
    logger.info("loaded %d samples (dataset_hash=%s)", len(samples), ds_hash)

    # Probe live rate limits per (provider, model). YAML-set values still win.
    await apply_discovered_limits(specs)
    for spec in specs:
        if spec.rate_rpm is not None or spec.rate_tpm is not None:
            logger.info(
                "[%s] effective rate: rpm=%s tpm=%s",
                spec.id, spec.rate_rpm or "unlimited", spec.rate_tpm or "unlimited",
            )

    embedder: SBERTEmbedder | None = None
    store: KNNStore | None = None
    needs_eval_pool = any(
        s.prompt in {"few_shot_static", "few_shot_retrieved"}
        and s.exemplar_dataset is None
        for s in specs
    )
    needs_retrieved_embedder = any(s.prompt == "few_shot_retrieved" for s in specs)
    if needs_eval_pool or needs_retrieved_embedder:
        embedder = SBERTEmbedder(name=specs[0].embedder_model)
    if needs_eval_pool:
        embeddings_file = Path(embeddings_path)
        if embeddings_file.exists():
            store = KNNStore.load(embeddings_file, samples)
        else:
            logger.info("building embeddings → %s", embeddings_file)
            store = KNNStore.build(samples, embedder)
            store.save(embeddings_file)

    # Held-out exemplar pools, keyed by path so multiple specs sharing a pool
    # pay one load each. Static-only experiments need just the JSONL; retrieved
    # also needs a corresponding npz (validated up-front by
    # `_validate_exemplar_config`).
    exemplar_samples_by_path: dict[str, list[Sample]] = {}
    exemplar_stores_by_path: dict[tuple[str, str], KNNStore] = {}
    for spec in specs:
        if spec.exemplar_dataset is None:
            continue
        if spec.exemplar_dataset not in exemplar_samples_by_path:
            ex_samples = load_dataset(spec.exemplar_dataset)
            exemplar_samples_by_path[spec.exemplar_dataset] = ex_samples
            logger.info(
                "loaded %d exemplar samples from %s",
                len(ex_samples), spec.exemplar_dataset,
            )
        if spec.prompt == "few_shot_retrieved" and spec.exemplar_embeddings is not None:
            store_key = (spec.exemplar_dataset, spec.exemplar_embeddings)
            if store_key not in exemplar_stores_by_path:
                exemplar_stores_by_path[store_key] = KNNStore.load(
                    Path(spec.exemplar_embeddings),
                    exemplar_samples_by_path[spec.exemplar_dataset],
                )
                logger.info(
                    "loaded exemplar KNN store from %s",
                    spec.exemplar_embeddings,
                )

    cache = ResultCache(cache_path)
    results_dir = Path(results_dir)
    results_dir.mkdir(parents=True, exist_ok=True)

    outputs: dict[str, str] = {}
    progress = Progress(
        SpinnerColumn(),
        TextColumn("[bold cyan]{task.fields[exp_id]}[/bold cyan]"),
        TextColumn("[dim]{task.fields[model]}[/dim]"),
        BarColumn(),
        MofNCompleteColumn(),
        TextColumn("[green]✓{task.fields[ok]}[/green] [red]✗{task.fields[err]}[/red]"),
        TimeElapsedColumn(),
        TextColumn("eta"),
        TimeRemainingColumn(),
        # Share the CLI's Console so RichHandler logs and the progress bar
        # render through the same Live context. Without this, logs go to
        # one stderr handle and the bar to another, so each log scrolls a
        # fresh copy of the bar instead of redrawing it in place.
        console=console,
        transient=False,
    )

    try:
        with progress:
            for spec in specs:
                task_id = progress.add_task(
                    "running",
                    total=len(samples),
                    exp_id=spec.id,
                    model=f"{spec.model} · {spec.prompt}",
                    ok=0,
                    err=0,
                )

                # Capture per-task counters by closure (default-arg trick locks
                # task_id to the current iteration's value).
                counters = {"ok": 0, "err": 0}

                def _on_done(r: PerPostResult, _tid=task_id, _c=counters) -> None:
                    if r.error:
                        _c["err"] += 1
                    else:
                        _c["ok"] += 1
                    progress.update(_tid, advance=1, ok=_c["ok"], err=_c["err"])

                ex_samples = (
                    exemplar_samples_by_path.get(spec.exemplar_dataset)
                    if spec.exemplar_dataset
                    else None
                )
                ex_store = (
                    exemplar_stores_by_path.get(
                        (spec.exemplar_dataset, spec.exemplar_embeddings)
                    )
                    if spec.exemplar_dataset and spec.exemplar_embeddings
                    else None
                )
                per_post = await run_experiment(
                    spec, samples, cache, store, embedder,
                    exemplar_samples=ex_samples,
                    exemplar_store=ex_store,
                    on_post_done=_on_done,
                )
                df = pd.DataFrame([_row(p, ds_hash) for p in per_post])
                out_path = results_dir / f"{spec.id}.parquet"
                df.to_parquet(out_path, index=False)
                outputs[spec.id] = str(out_path)
                progress.console.log(
                    f"[bold green]done[/bold green] {spec.id} → {out_path} "
                    f"(ok={counters['ok']}, err={counters['err']})"
                )
    finally:
        cache.close()
    return outputs


def _row(p: PerPostResult, ds_hash: str) -> dict[str, Any]:
    return {
        "experiment_id": p.experiment_id,
        "post_id": p.post_id,
        "fold": p.fold,
        "gt_score": p.gt_score,
        "gt_emotion": p.gt_emotion,
        "gt_aspect": p.gt_aspect,
        "pred_relevant": p.pred_relevant,
        "pred_score": p.pred_score,
        "pred_emotion": p.pred_emotion,
        "pred_aspect_raw": p.pred_aspect_raw,
        "pred_aspect_normalized": p.pred_aspect_normalized,
        "error": p.error,
        "latency_ms": p.latency_ms,
        "prompt_tokens": p.prompt_tokens,
        "completion_tokens": p.completion_tokens,
        "total_tokens": p.total_tokens,
        "cached_tokens": p.cached_tokens,
        "fewshot_post_ids": p.fewshot_post_ids,
        "dataset_hash": ds_hash,
    }
