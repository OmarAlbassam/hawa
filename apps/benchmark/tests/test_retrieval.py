"""Retrieval correctness — and most importantly, leakage prevention.

We use a fake `Embedder` that returns deterministic 2D vectors so the
test doesn't depend on downloading sentence-transformers in CI.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pytest

from benchmark.dataset import GroundTruth, Sample, assign_folds
from benchmark.retrieval.store import KNNStore


@dataclass
class FakeEmbedder:
    name: str = "fake"
    dim: int = 2

    def encode(self, texts):
        # Place each text on the unit circle. Texts that are equal land on
        # the same point, so cosine similarity = 1; close ids land close.
        out = []
        for t in texts:
            angle = (sum(ord(c) for c in t) % 360) * np.pi / 180
            out.append([np.cos(angle), np.sin(angle)])
        v = np.array(out, dtype=np.float32)
        # Already on unit circle, but normalize defensively.
        v /= np.linalg.norm(v, axis=1, keepdims=True)
        return v


def _samples(texts, post_ids=None):
    post_ids = post_ids or list(range(len(texts)))
    return [
        Sample(
            post_id=pid,
            text=t,
            gt=GroundTruth(score=3.0, emotion="JOY", aspect="PRODUCT"),
        )
        for pid, t in zip(post_ids, texts)
    ]


def test_query_returns_top_k_by_similarity():
    samples = _samples(["alpha", "alphb", "zeta"])
    store = KNNStore.build(samples, FakeEmbedder())
    results = store.query("alpha", FakeEmbedder(), k=2)
    assert len(results) == 2
    # Self-match first (the index includes the query text), then the close one.
    assert results[0].sample.text == "alpha"


def test_query_excludes_post_id():
    samples = _samples(["alpha", "alphb", "zeta"], post_ids=[10, 11, 12])
    store = KNNStore.build(samples, FakeEmbedder())
    results = store.query(
        "alpha", FakeEmbedder(), k=3, exclude_post_ids={10}
    )
    assert all(r.sample.post_id != 10 for r in results)


def test_query_excludes_fold_for_leakage_prevention():
    # Two samples in fold 0 (the test post's fold) must not appear when
    # exclude_folds={0}, even if they're the most similar.
    raw = _samples(["alpha", "alphb", "alphc", "zeta"], post_ids=[1, 2, 3, 4])
    forced = [
        Sample(post_id=1, text="alpha", gt=raw[0].gt, fold=0),
        Sample(post_id=2, text="alphb", gt=raw[1].gt, fold=0),  # same fold as query
        Sample(post_id=3, text="alphc", gt=raw[2].gt, fold=1),
        Sample(post_id=4, text="zeta", gt=raw[3].gt, fold=2),
    ]
    store = KNNStore.build(forced, FakeEmbedder())
    results = store.query(
        "alpha", FakeEmbedder(), k=3,
        exclude_post_ids={1},
        exclude_folds={0},
    )
    returned_ids = [r.sample.post_id for r in results]
    assert 1 not in returned_ids
    assert 2 not in returned_ids  # excluded by fold, not just by post_id


def test_save_load_roundtrip(tmp_path):
    samples = _samples(["a", "b", "c"], post_ids=[7, 8, 9])
    store = KNNStore.build(samples, FakeEmbedder())
    path = tmp_path / "emb.npz"
    store.save(path)

    loaded = KNNStore.load(path, samples)
    np.testing.assert_array_equal(store.embeddings, loaded.embeddings)
    assert [s.post_id for s in loaded.samples] == [7, 8, 9]


def test_load_detects_dataset_drift(tmp_path):
    samples = _samples(["a", "b", "c"], post_ids=[1, 2, 3])
    store = KNNStore.build(samples, FakeEmbedder())
    path = tmp_path / "emb.npz"
    store.save(path)

    new_samples = _samples(["a", "b"], post_ids=[1, 2])  # one missing
    with pytest.raises(ValueError, match="out of sync"):
        KNNStore.load(path, new_samples)
