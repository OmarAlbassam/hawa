from __future__ import annotations

import json

import pytest

from benchmark.dataset import (
    GroundTruth,
    Sample,
    assign_folds,
    dataset_hash,
    iter_folds,
    load_dataset,
)


def _write_jsonl(path, rows):
    with open(path, "w") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")


def test_load_dataset_parses_rows(tmp_path):
    p = tmp_path / "data.jsonl"
    _write_jsonl(p, [
        {"post_id": 1, "text": "x", "gt_score": 4.0, "gt_emotion": "JOY", "gt_aspect": "PRODUCT"},
        {"post_id": 2, "text": "y", "gt_score": 1.0, "gt_emotion": "ANGER", "gt_aspect": "DELIVERY"},
    ])
    samples = load_dataset(p)
    assert len(samples) == 2
    assert samples[0].post_id == 1
    assert samples[0].gt.score == 4.0


def test_load_dataset_rejects_out_of_range_score(tmp_path):
    p = tmp_path / "data.jsonl"
    _write_jsonl(p, [
        {"post_id": 1, "text": "x", "gt_score": 9.0, "gt_emotion": "JOY", "gt_aspect": "PRODUCT"},
    ])
    with pytest.raises(ValueError, match="score out of range"):
        load_dataset(p)


def test_load_dataset_rejects_unknown_emotion(tmp_path):
    p = tmp_path / "data.jsonl"
    _write_jsonl(p, [
        {"post_id": 1, "text": "x", "gt_score": 3.0, "gt_emotion": "ECSTASY", "gt_aspect": "PRODUCT"},
    ])
    with pytest.raises(ValueError, match="emotion"):
        load_dataset(p)


def test_assign_folds_is_deterministic():
    samples = [_sample(i) for i in range(20)]
    a = assign_folds(samples, k=5)
    b = assign_folds(samples, k=5)
    assert [s.fold for s in a] == [s.fold for s in b]
    assert all(0 <= s.fold < 5 for s in a)


def test_iter_folds_partitions_disjointly():
    samples = assign_folds([_sample(i) for i in range(50)], k=5)
    seen_test = set()
    for fold_idx, train, test in iter_folds(samples, k=5):
        test_ids = {s.post_id for s in test}
        train_ids = {s.post_id for s in train}
        assert test_ids.isdisjoint(train_ids)
        assert all(s.fold == fold_idx for s in test)
        assert all(s.fold != fold_idx for s in train)
        seen_test |= test_ids
    assert seen_test == {s.post_id for s in samples}


def test_dataset_hash_is_stable_across_order():
    a = [_sample(1), _sample(2), _sample(3)]
    b = [_sample(3), _sample(1), _sample(2)]
    assert dataset_hash(a) == dataset_hash(b)


def test_dataset_hash_changes_when_label_changes():
    a = [_sample(1, score=3.0)]
    b = [_sample(1, score=4.0)]
    assert dataset_hash(a) != dataset_hash(b)


def _sample(post_id: int, score: float = 3.0) -> Sample:
    return Sample(
        post_id=post_id,
        text=f"text-{post_id}",
        gt=GroundTruth(score=score, emotion="JOY", aspect="PRODUCT"),
    )
