"""Tests for CSV → JSONL importer."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from benchmark.import_csv import ColumnMap, import_csv_to_jsonl


def _write_csv(path: Path, rows: list[str]) -> None:
    path.write_text("\n".join(rows) + "\n")


def test_import_default_columns(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "date,url,post_text,score,aspect,emotion",
        "01/01/2025,https://x/1,Loved the new iPhone,4.5,PRODUCT,JOY",
        "01/01/2025,https://x/2,Shipping took forever,1.5,DELIVERY,ANGER",
    ])
    out = tmp_path / "control.jsonl"
    report = import_csv_to_jsonl(src, out)

    assert report.n_rows == 2
    assert report.n_invalid_score == 0
    assert report.n_invalid_emotion == 0
    assert report.n_invalid_aspect == 0

    rows = [json.loads(line) for line in out.read_text().splitlines()]
    assert rows[0]["post_id"] == 1
    assert rows[0]["gt_score"] == 4.5
    assert rows[0]["gt_emotion"] == "JOY"
    assert rows[0]["gt_aspect"] == "PRODUCT"
    # Extra CSV columns land in metadata.
    assert rows[0]["metadata"]["date"] == "01/01/2025"
    assert rows[0]["metadata"]["url"] == "https://x/1"


def test_import_skips_empty_text(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "post_text,score,aspect,emotion",
        "Real post,3.0,PRODUCT,NEUTRAL",
        ",4.0,PRODUCT,JOY",
        "   ,1.0,DELIVERY,ANGER",
    ])
    out = tmp_path / "control.jsonl"
    report = import_csv_to_jsonl(src, out)

    assert report.n_rows == 1
    assert report.n_skipped_empty == 2


def test_import_records_invalid_taxonomy_values(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "post_text,score,aspect,emotion",
        "ok,3.0,PRODUCT,NEUTRAL",
        "bad emo,3.0,PRODUCT,ECSTASY",
        "bad asp,3.0,VIBES,JOY",
    ])
    out = tmp_path / "control.jsonl"
    report = import_csv_to_jsonl(src, out, skip_invalid=False)

    assert report.n_rows == 3  # all written
    assert report.n_invalid_emotion == 1
    assert report.n_invalid_aspect == 1
    assert report.invalid_emotion_values == {"ECSTASY": 1}
    assert report.invalid_aspect_values == {"VIBES": 1}


def test_import_skip_invalid_drops_rows(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "post_text,score,aspect,emotion",
        "ok,3.0,PRODUCT,NEUTRAL",
        "bad,3.0,VIBES,JOY",
    ])
    out = tmp_path / "control.jsonl"
    report = import_csv_to_jsonl(src, out, skip_invalid=True)

    assert report.n_rows == 1


def test_import_uses_post_id_column_when_provided(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "id,post_text,score,aspect,emotion",
        "100,a,3.0,PRODUCT,NEUTRAL",
        "200,b,4.0,PRODUCT,JOY",
    ])
    out = tmp_path / "control.jsonl"
    report = import_csv_to_jsonl(src, out, columns=ColumnMap(post_id="id"))

    assert report.n_rows == 2
    rows = [json.loads(line) for line in out.read_text().splitlines()]
    assert [r["post_id"] for r in rows] == [100, 200]


def test_import_missing_column_raises(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "text,score,aspect",  # missing 'emotion'
        "a,3.0,PRODUCT",
    ])
    with pytest.raises(ValueError, match="missing required columns"):
        import_csv_to_jsonl(src, tmp_path / "out.jsonl", columns=ColumnMap(text="text"))


def test_import_normalizes_case(tmp_path):
    src = tmp_path / "labels.csv"
    _write_csv(src, [
        "post_text,score,aspect,emotion",
        "x,3.0,product,joy",
    ])
    out = tmp_path / "control.jsonl"
    import_csv_to_jsonl(src, out)
    row = json.loads(out.read_text())
    assert row["gt_aspect"] == "PRODUCT"
    assert row["gt_emotion"] == "JOY"
