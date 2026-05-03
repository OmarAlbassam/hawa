"""CSV → JSONL conversion for the benchmark control dataset.

The team labels in spreadsheets/CSV. The runner consumes JSONL with a fixed
schema. This module bridges them.

Defaults match `Apple_dataset_relabeled.csv`'s columns
(`post_text, score, aspect, emotion`); override via flags if your CSV
differs. `date` and `url` columns (or anything else not in the schema map)
are preserved into per-row `metadata` so the original context isn't lost
— useful in the notebook when you want to read the source post on Twitter.

post_id is 1-indexed row number. The dataset_hash captures full content,
so reproducibility doesn't rely on stable ids across re-exports.
"""

from __future__ import annotations

import csv
import json
from dataclasses import dataclass
from pathlib import Path

from benchmark.dataset import ASPECT_LABELS, EMOTION_LABELS


@dataclass
class ColumnMap:
    """Maps source-CSV column names to benchmark JSONL field names."""

    text: str = "post_text"
    score: str = "score"
    emotion: str = "emotion"
    aspect: str = "aspect"
    is_relevant: str | None = None  # optional column
    post_id: str | None = None  # optional column — generated from row number if absent


@dataclass
class ImportReport:
    n_rows: int
    n_skipped_empty: int
    n_invalid_score: int
    n_invalid_emotion: int
    n_invalid_aspect: int
    invalid_emotion_values: dict[str, int]
    invalid_aspect_values: dict[str, int]
    out_path: Path


def import_csv_to_jsonl(
    csv_path: str | Path,
    out_path: str | Path,
    *,
    columns: ColumnMap | None = None,
    skip_invalid: bool = False,
) -> ImportReport:
    """Convert a labeled CSV into the benchmark's JSONL format.

    `skip_invalid=False` (default) writes every row and lets the dataset
    loader's validators surface problems with line numbers — that's usually
    what you want, because silent skips hide labeling bugs. Pass
    `skip_invalid=True` for a strict-import that drops rows the loader
    would reject.
    """
    csv_path = Path(csv_path)
    out_path = Path(out_path)
    columns = columns or ColumnMap()

    invalid_emotions: dict[str, int] = {}
    invalid_aspects: dict[str, int] = {}
    n_skipped_empty = 0
    n_invalid_score = 0
    n_invalid_emotion = 0
    n_invalid_aspect = 0
    written_rows: list[dict] = []

    with csv_path.open() as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None:
            raise ValueError(f"{csv_path} has no header row")
        _verify_columns(columns, reader.fieldnames, csv_path)

        for row_idx, raw in enumerate(reader, start=1):
            text = (raw.get(columns.text) or "").strip()
            if not text:
                n_skipped_empty += 1
                continue

            post_id = _resolve_post_id(raw, columns, row_idx)
            score_raw = raw.get(columns.score, "")
            emotion = (raw.get(columns.emotion) or "").strip().upper()
            aspect = (raw.get(columns.aspect) or "").strip().upper()

            try:
                score = float(score_raw)
                if not (0.0 <= score <= 5.0):
                    raise ValueError
            except (TypeError, ValueError):
                n_invalid_score += 1
                if skip_invalid:
                    continue
                score = 0.0  # let the loader's validator complain with line no.

            if emotion not in EMOTION_LABELS:
                n_invalid_emotion += 1
                invalid_emotions[emotion] = invalid_emotions.get(emotion, 0) + 1
                if skip_invalid:
                    continue
            if aspect not in ASPECT_LABELS:
                n_invalid_aspect += 1
                invalid_aspects[aspect] = invalid_aspects.get(aspect, 0) + 1
                if skip_invalid:
                    continue

            metadata = {
                k: v for k, v in raw.items()
                if k not in {columns.text, columns.score, columns.emotion, columns.aspect, columns.is_relevant, columns.post_id}
                and v not in (None, "")
            }

            obj: dict = {
                "post_id": post_id,
                "text": text,
                "gt_score": score,
                "gt_emotion": emotion,
                "gt_aspect": aspect,
            }
            if columns.is_relevant and raw.get(columns.is_relevant) not in (None, ""):
                obj["gt_is_relevant"] = _coerce_bool(raw[columns.is_relevant])
            if metadata:
                obj["metadata"] = metadata
            written_rows.append(obj)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w") as f:
        for obj in written_rows:
            f.write(json.dumps(obj, ensure_ascii=False) + "\n")

    return ImportReport(
        n_rows=len(written_rows),
        n_skipped_empty=n_skipped_empty,
        n_invalid_score=n_invalid_score,
        n_invalid_emotion=n_invalid_emotion,
        n_invalid_aspect=n_invalid_aspect,
        invalid_emotion_values=invalid_emotions,
        invalid_aspect_values=invalid_aspects,
        out_path=out_path,
    )


def _verify_columns(columns: ColumnMap, headers: list[str], csv_path: Path) -> None:
    headers_set = set(headers)
    required = {columns.text, columns.score, columns.emotion, columns.aspect}
    missing = required - headers_set
    if missing:
        raise ValueError(
            f"{csv_path} is missing required columns: {sorted(missing)}. "
            f"Found columns: {headers}. Override via the --col-* flags."
        )


def _resolve_post_id(row: dict, columns: ColumnMap, row_idx: int) -> int:
    if columns.post_id and row.get(columns.post_id):
        try:
            return int(row[columns.post_id])
        except (TypeError, ValueError) as e:
            raise ValueError(
                f"row {row_idx}: post_id column {columns.post_id!r}={row[columns.post_id]!r} is not an integer"
            ) from e
    return row_idx


def _coerce_bool(value: str) -> bool:
    return str(value).strip().lower() in {"1", "true", "yes", "y", "t"}
