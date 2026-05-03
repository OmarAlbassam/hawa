"""Control dataset loading, taxonomies, and k-fold splitting.

The control dataset is hand-labeled by the team (~100s of posts) with three
ground-truth fields: a sentiment score in [0, 5], a 7-way emotion label, and
an aspect label drawn from the production `Aspect` enum in `apps/llm/models.py`.

Folds are assigned deterministically from `post_id` so reruns produce the same
splits and the few-shot retriever's leakage prevention is reproducible.
"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Iterator
from dataclasses import dataclass, field
from pathlib import Path

# Reuse production taxonomies. If you need to add an emotion or aspect, add it
# to apps/llm/models.py first — that's the authoritative definition.
from models import Aspect, Emotion

EMOTION_LABELS: tuple[str, ...] = tuple(e.value for e in Emotion)
ASPECT_LABELS: tuple[str, ...] = tuple(a.value for a in Aspect)


@dataclass(frozen=True)
class GroundTruth:
    score: float
    emotion: str
    aspect: str
    is_relevant: bool = True

    def __post_init__(self) -> None:
        if not (0.0 <= self.score <= 5.0):
            raise ValueError(f"score out of range: {self.score}")
        if self.is_relevant:
            if self.emotion not in EMOTION_LABELS:
                raise ValueError(
                    f"emotion {self.emotion!r} not in taxonomy {EMOTION_LABELS}"
                )
            if self.aspect not in ASPECT_LABELS:
                raise ValueError(
                    f"aspect {self.aspect!r} not in taxonomy {ASPECT_LABELS}"
                )


@dataclass(frozen=True)
class Sample:
    post_id: int
    text: str
    gt: GroundTruth
    fold: int = 0
    metadata: dict = field(default_factory=dict)


def load_dataset(path: str | Path) -> list[Sample]:
    """Load samples from a JSONL file.

    Expected fields per line:
      post_id (int), text (str),
      gt_score (float), gt_emotion (str), gt_aspect (str),
      [optional] gt_is_relevant (bool, default true),
      [optional] fold (int)
    """
    path = Path(path)
    samples: list[Sample] = []
    with path.open() as f:
        for line_no, raw in enumerate(f, start=1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                row = json.loads(raw)
            except json.JSONDecodeError as e:
                raise ValueError(f"{path}:{line_no} invalid JSON: {e}") from e
            samples.append(_row_to_sample(row, path, line_no))
    if not samples:
        raise ValueError(f"{path} contained no samples")
    return samples


def _row_to_sample(row: dict, path: Path, line_no: int) -> Sample:
    try:
        gt = GroundTruth(
            score=float(row["gt_score"]),
            emotion=str(row["gt_emotion"]).upper(),
            aspect=str(row["gt_aspect"]).upper(),
            is_relevant=bool(row.get("gt_is_relevant", True)),
        )
        return Sample(
            post_id=int(row["post_id"]),
            text=str(row["text"]),
            gt=gt,
            fold=int(row.get("fold", 0)),
            metadata=row.get("metadata", {}),
        )
    except (KeyError, ValueError) as e:
        raise ValueError(f"{path}:{line_no} {e}") from e


def assign_folds(samples: list[Sample], k: int = 5) -> list[Sample]:
    """Deterministically assign each sample to a fold in [0, k).

    Fold = hash(post_id) % k. Stable across runs and platforms (uses sha256,
    not Python's randomized hash).
    """
    if k < 2:
        raise ValueError(f"k must be >= 2, got {k}")
    out: list[Sample] = []
    for s in samples:
        digest = hashlib.sha256(str(s.post_id).encode()).digest()
        fold = int.from_bytes(digest[:8], "big") % k
        out.append(
            Sample(
                post_id=s.post_id,
                text=s.text,
                gt=s.gt,
                fold=fold,
                metadata=s.metadata,
            )
        )
    return out


def iter_folds(samples: list[Sample], k: int) -> Iterator[tuple[int, list[Sample], list[Sample]]]:
    """Yield (fold_index, train, test) tuples.

    Caller is responsible for having already assigned folds (call `assign_folds`).
    """
    by_fold: dict[int, list[Sample]] = {i: [] for i in range(k)}
    for s in samples:
        if s.fold not in by_fold:
            raise ValueError(f"sample {s.post_id} has fold {s.fold}, k={k}")
        by_fold[s.fold].append(s)
    for fold_idx in range(k):
        test = by_fold[fold_idx]
        train = [s for f, group in by_fold.items() if f != fold_idx for s in group]
        yield fold_idx, train, test


def dataset_hash(samples: list[Sample]) -> str:
    """Stable digest of the dataset for reproducibility metadata."""
    h = hashlib.sha256()
    for s in sorted(samples, key=lambda x: x.post_id):
        h.update(str(s.post_id).encode())
        h.update(b"\x00")
        h.update(s.text.encode())
        h.update(b"\x00")
        h.update(f"{s.gt.score:.4f}|{s.gt.emotion}|{s.gt.aspect}".encode())
        h.update(b"\n")
    return h.hexdigest()[:16]
