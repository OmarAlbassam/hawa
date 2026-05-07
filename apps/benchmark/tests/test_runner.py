"""Tests for ExperimentSpec → Settings plumbing.

The runner lets each YAML experiment override LLM-parameter defaults
(`max_tokens`, `reasoning_effort`). These tests pin the path from YAML
through `expand_matrix` and `build_settings_for_experiment`.

Also covers a `model_copy` quirk: it does not re-run pydantic validators,
so any field whose value comes from the `apply_provider_defaults` validator
stays at the *base* provider's value unless the runner re-overrides it
manually. That bites every PROVIDER_DEFAULTS field — `base_url` already
had an explicit override; `request_timeout_s` (added later) needed one too.
"""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from benchmark.dataset import GroundTruth, Sample
from benchmark.runner import (
    ExperimentSpec,
    _build_prompt,
    _validate_exemplar_config,
    build_settings_for_experiment,
    expand_matrix,
)


def _spec(**overrides) -> ExperimentSpec:
    base = dict(
        id="test",
        provider="fireworks",
        model="accounts/fireworks/models/gpt-oss-20b",
        temperature=0.0,
        prompt="zero_shot",
    )
    base.update(overrides)
    return ExperimentSpec(**base)


def test_max_tokens_override_flows_into_settings():
    spec = _spec(max_tokens=1024)
    settings = build_settings_for_experiment(spec)
    assert settings.max_tokens == 1024


def test_reasoning_effort_override_flows_into_settings():
    spec = _spec(reasoning_effort="low")
    settings = build_settings_for_experiment(spec)
    assert settings.reasoning_effort == "low"


def test_unset_overrides_inherit_settings_defaults():
    """No YAML knob → Settings default (LLM_MAX_TOKENS=512, no reasoning_effort)."""
    spec = _spec()
    settings = build_settings_for_experiment(spec)
    assert settings.max_tokens == 512
    assert settings.reasoning_effort is None


def test_expand_matrix_picks_up_max_tokens_and_reasoning_effort():
    config = {
        "experiments": [
            {
                "id": "gpt-oss",
                "provider": "fireworks",
                "model": "accounts/fireworks/models/gpt-oss-20b",
                "prompt": "zero_shot",
                "temperature": 0.0,
                "max_tokens": 2048,
                "reasoning_effort": "low",
            }
        ]
    }
    specs = expand_matrix(config)
    assert len(specs) == 1
    assert specs[0].max_tokens == 2048
    assert specs[0].reasoning_effort == "low"


def test_expand_matrix_omits_overrides_when_yaml_doesnt_set_them():
    config = {
        "experiments": [
            {
                "id": "default",
                "provider": "ollama",
                "model": "llama3.1:8b",
                "prompt": "zero_shot",
                "temperature": 0.0,
            }
        ]
    }
    specs = expand_matrix(config)
    assert specs[0].max_tokens is None
    assert specs[0].reasoning_effort is None
    assert specs[0].exemplar_dataset is None
    assert specs[0].exemplar_embeddings is None


# --- Held-out exemplar pool ---


def _sample(post_id: int, text: str, *, score: float = 3.0) -> Sample:
    return Sample(
        post_id=post_id,
        text=text,
        gt=GroundTruth(score=score, emotion="JOY", aspect="PRODUCT"),
        fold=0,
    )


def test_expand_matrix_picks_up_exemplar_fields():
    config = {
        "experiments": [
            {
                "id": "static_holdout",
                "provider": "ollama",
                "model": "llama3.1:8b",
                "prompt": "few_shot_static",
                "temperature": 0.0,
                "fewshot_static_ids": [1, 2],
                "exemplar_dataset": "data/exemplars.jsonl",
                "exemplar_embeddings": "data/exemplars.npz",
            }
        ]
    }
    specs = expand_matrix(config)
    assert specs[0].exemplar_dataset == "data/exemplars.jsonl"
    assert specs[0].exemplar_embeddings == "data/exemplars.npz"


def test_expand_matrix_picks_up_embedder_provider():
    """A YAML `embedder_provider: fireworks` should land on the spec; default is sbert."""
    config = {
        "experiments": [
            {
                "id": "fw_retrieved",
                "provider": "fireworks",
                "model": "accounts/fireworks/models/llama-v3p3-70b-instruct",
                "prompt": "few_shot_retrieved",
                "temperature": 0.0,
                "fewshot_k": 4,
                "embedder_provider": "fireworks",
                "embedder_model": "nomic-ai/nomic-embed-text-v1.5",
                "exemplar_dataset": "data/exemplars.jsonl",
                "exemplar_embeddings": "data/exemplars-embeddings-nomic.npz",
            },
            {
                "id": "sbert_default",
                "provider": "groq",
                "model": "llama-3.3-70b-versatile",
                "prompt": "zero_shot",
                "temperature": 0.0,
            },
        ]
    }
    specs = expand_matrix(config)
    assert specs[0].embedder_provider == "fireworks"
    assert specs[0].embedder_model == "nomic-ai/nomic-embed-text-v1.5"
    assert specs[1].embedder_provider == "sbert"  # default


def test_static_with_exemplar_pool_resolves_against_pool_not_eval():
    """If an exemplar id collides with an eval id, the exemplar pool wins.

    Concretely: eval has post 1 = "EVAL post"; exemplar pool has post 1 =
    "EXEMPLAR post". Asking for fewshot_static_ids=[1] must produce a prompt
    containing "EXEMPLAR post", proving the lookup table came from the pool.
    """
    eval_samples = [_sample(1, "EVAL post for testing")]
    pool_samples = [
        _sample(1, "EXEMPLAR post about a cool product"),
        _sample(2, "Another exemplar mentioning service"),
    ]
    eval_store = MagicMock()
    eval_store.samples = eval_samples

    spec = _spec(prompt="few_shot_static", fewshot_static_ids=[1, 2])
    test_post = _sample(99, "the post being analyzed")

    prompt, ids = _build_prompt(
        spec, test_post, eval_store, embedder=None,
        exemplar_samples=pool_samples,
    )

    assert "EXEMPLAR post about a cool product" in prompt
    assert "EVAL post for testing" not in prompt
    assert ids == [1, 2]


def test_static_without_exemplar_pool_falls_back_to_eval_store():
    """Backwards-compat: omitting exemplar_samples uses store.samples as today."""
    eval_samples = [_sample(1, "EVAL post text")]
    eval_store = MagicMock()
    eval_store.samples = eval_samples

    spec = _spec(prompt="few_shot_static", fewshot_static_ids=[1])
    test_post = _sample(99, "the post being analyzed")

    prompt, ids = _build_prompt(spec, test_post, eval_store, embedder=None)
    assert "EVAL post text" in prompt
    assert ids == [1]


def test_retrieved_with_exemplar_store_drops_fold_mask():
    """When the kNN store comes from a held-out exemplar dataset, fold values
    in that file are meaningless relative to the eval split. The runner must
    pass exclude_folds=set() so the retriever masks nothing by fold."""
    spec = _spec(prompt="few_shot_retrieved", fewshot_k=3)
    test_post = _sample(42, "sample text")
    test_post = Sample(
        post_id=42, text="sample text",
        gt=GroundTruth(score=3.0, emotion="JOY", aspect="PRODUCT"),
        fold=2,
    )

    fake_exemplar_store = MagicMock()
    fake_exemplar_store.query.return_value = []
    embedder = MagicMock()

    _build_prompt(
        spec, test_post, store=None, embedder=embedder,
        exemplar_store=fake_exemplar_store,
    )

    fake_exemplar_store.query.assert_called_once()
    kwargs = fake_exemplar_store.query.call_args.kwargs
    assert kwargs["exclude_folds"] == set()
    # Self-id mask still applied as a defensive no-op against id collisions.
    assert kwargs["exclude_post_ids"] == {42}


def test_retrieved_without_exemplar_store_keeps_fold_mask():
    """Backwards-compat: default path masks the test post's own fold."""
    test_post = Sample(
        post_id=42, text="sample text",
        gt=GroundTruth(score=3.0, emotion="JOY", aspect="PRODUCT"),
        fold=2,
    )
    spec = _spec(prompt="few_shot_retrieved", fewshot_k=3)
    fake_eval_store = MagicMock()
    fake_eval_store.query.return_value = []
    embedder = MagicMock()

    _build_prompt(spec, test_post, store=fake_eval_store, embedder=embedder)

    kwargs = fake_eval_store.query.call_args.kwargs
    assert kwargs["exclude_folds"] == {2}


def test_validator_rejects_retrieved_with_dataset_but_no_embeddings(tmp_path: Path):
    ds = tmp_path / "exemplars.jsonl"
    ds.write_text(json.dumps({
        "post_id": 1, "text": "x", "gt_score": 3.0,
        "gt_emotion": "JOY", "gt_aspect": "PRODUCT",
    }) + "\n")

    spec = _spec(
        prompt="few_shot_retrieved",
        fewshot_k=3,
        exemplar_dataset=str(ds),
        exemplar_embeddings=None,
    )
    with pytest.raises(RuntimeError, match="exemplar_embeddings"):
        _validate_exemplar_config([spec])


def test_validator_rejects_missing_exemplar_dataset_file(tmp_path: Path):
    spec = _spec(
        prompt="few_shot_static",
        fewshot_static_ids=[1],
        exemplar_dataset=str(tmp_path / "does-not-exist.jsonl"),
    )
    with pytest.raises(RuntimeError, match="does not exist"):
        _validate_exemplar_config([spec])


def test_validator_passes_when_files_exist(tmp_path: Path):
    ds = tmp_path / "exemplars.jsonl"
    ds.write_text(json.dumps({
        "post_id": 1, "text": "x", "gt_score": 3.0,
        "gt_emotion": "JOY", "gt_aspect": "PRODUCT",
    }) + "\n")
    npz = tmp_path / "exemplars.npz"
    npz.write_bytes(b"")  # existence check only

    spec_static = _spec(
        prompt="few_shot_static",
        fewshot_static_ids=[1],
        exemplar_dataset=str(ds),
    )
    spec_retrieved = _spec(
        prompt="few_shot_retrieved",
        fewshot_k=3,
        exemplar_dataset=str(ds),
        exemplar_embeddings=str(npz),
    )
    _validate_exemplar_config([spec_static, spec_retrieved])  # no raise


# ---------------------------------------------------------------------------
# Per-provider timeout regression — `model_copy(update=...)` does not re-run
# `apply_provider_defaults`, so the runner must re-apply each PROVIDER_DEFAULTS
# field manually. These tests pin that for `request_timeout_s`.
# ---------------------------------------------------------------------------


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


def _provider_spec(provider: str, model: str = "llama-3.1-8b-instant") -> ExperimentSpec:
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
    settings = build_settings_for_experiment(_provider_spec("groq"))
    assert settings.request_timeout_s == 60.0
    assert settings.base_url == "https://api.groq.com/openai/v1"


def test_ollama_experiment_uses_ollama_timeout(env_provider_unset):
    settings = build_settings_for_experiment(
        _provider_spec("ollama", model="llama3.1:8b")
    )
    assert settings.request_timeout_s == 300.0
