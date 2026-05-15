"""Tests for the report aggregation layer.

These cover the bugs that motivated the rewrite:
- Aspect labels were double-counted (`*ASPECT_LABELS, OTHER` listed OTHER twice)
- Macro-F1 averaged over zero-support classes (FEAR, DELIVERY)
- MAE was computed on each experiment's own subset, not the intersection
"""

from __future__ import annotations

from pathlib import Path

import pandas as pd
import pytest

from benchmark.report import (
    _compute_intersection,
    _present_labels,
    _summarize_one,
    summarize,
)


# Full taxonomy columns expected by _summarize_one.
BASE_COLUMNS = [
    "experiment_id", "post_id", "fold", "gt_score", "gt_emotion", "gt_aspect",
    "pred_relevant", "pred_score", "pred_emotion", "pred_aspect_raw",
    "pred_aspect_normalized", "error", "latency_ms",
]


def _make_row(
    *,
    post_id: int,
    experiment_id: str = "test",
    gt_score: float,
    gt_emotion: str,
    gt_aspect: str,
    pred_score: float,
    pred_emotion: str,
    pred_aspect: str,
    pred_relevant: bool = True,
) -> dict:
    return {
        "experiment_id": experiment_id,
        "post_id": post_id,
        "fold": 0,
        "gt_score": gt_score,
        "gt_emotion": gt_emotion,
        "gt_aspect": gt_aspect,
        "pred_relevant": pred_relevant,
        "pred_score": pred_score,
        "pred_emotion": pred_emotion,
        "pred_aspect_raw": pred_aspect,
        "pred_aspect_normalized": pred_aspect,
        "error": None,
        "latency_ms": 100.0,
    }


def _make_df(rows: list[dict]) -> pd.DataFrame:
    df = pd.DataFrame(rows)
    # Ensure all base columns exist even if rows omit them.
    for col in BASE_COLUMNS:
        if col not in df.columns:
            df[col] = None
    return df


def test_present_labels_drops_zero_support():
    s = pd.Series(["JOY", "ANGER", "JOY"])
    labs = _present_labels(s, ("JOY", "ANGER", "FEAR", "NEUTRAL"))
    assert labs == ("JOY", "ANGER")  # FEAR/NEUTRAL excluded
    assert "FEAR" not in labs


def test_summarize_perfect_predictions_no_zero_support_drag():
    """Perfect predictions on 4 aspect classes should yield aspect_macro_f1=1.0,
    not 4/6 (the old bug averaged in DELIVERY/OTHER with F1=0)."""
    rows = [
        _make_row(post_id=1, gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=4.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
        _make_row(post_id=2, gt_score=1.0, gt_emotion="ANGER", gt_aspect="SERVICE",
                  pred_score=1.0, pred_emotion="ANGER", pred_aspect="SERVICE"),
        _make_row(post_id=3, gt_score=2.5, gt_emotion="NEUTRAL", gt_aspect="BRAND",
                  pred_score=2.5, pred_emotion="NEUTRAL", pred_aspect="BRAND"),
        _make_row(post_id=4, gt_score=0.5, gt_emotion="DISGUST", gt_aspect="PRICING",
                  pred_score=0.5, pred_emotion="DISGUST", pred_aspect="PRICING"),
    ]
    summary = _summarize_one(_make_df(rows), intersection=set())
    assert summary["aspect_macro_f1"] == pytest.approx(1.0)
    assert summary["emotion_macro_f1"] == pytest.approx(1.0)
    assert summary["score_mae"] == pytest.approx(0.0)
    assert summary["coverage"] == pytest.approx(1.0)


def test_summarize_excludes_fear_when_absent_from_gt():
    """If GT has no FEAR, macro-F1 averages over 4 classes, not 7."""
    rows = [
        _make_row(post_id=i, gt_score=2.5, gt_emotion=emo, gt_aspect="PRODUCT",
                  pred_score=2.5, pred_emotion=emo, pred_aspect="PRODUCT")
        for i, emo in enumerate(["JOY", "ANGER", "DISGUST", "SADNESS"])
    ]
    summary = _summarize_one(_make_df(rows), intersection=set())
    # 4 emotion classes, all perfect → 1.0, not (4*1.0 + 3*0.0)/7.
    assert summary["emotion_macro_f1"] == pytest.approx(1.0)


def test_summarize_aspect_other_not_double_counted():
    """Regression test for the historical `(*ASPECT_LABELS, OTHER)` bug."""
    rows = [
        _make_row(post_id=1, gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=4.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
        _make_row(post_id=2, gt_score=1.0, gt_emotion="ANGER", gt_aspect="BRAND",
                  pred_score=1.0, pred_emotion="ANGER", pred_aspect="BRAND"),
    ]
    summary = _summarize_one(_make_df(rows), intersection=set())
    # If OTHER were averaged in twice with F1=0, this would be 2/4=0.5, not 1.0.
    assert summary["aspect_macro_f1"] == pytest.approx(1.0)


def test_summarize_coverage_reflects_abstentions():
    rows = [
        _make_row(post_id=1, gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=4.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
        _make_row(post_id=2, gt_score=1.0, gt_emotion="ANGER", gt_aspect="BRAND",
                  pred_score=None, pred_emotion=None, pred_aspect=None,
                  pred_relevant=False),
    ]
    df = _make_df(rows)
    summary = _summarize_one(df, intersection=set())
    assert summary["n"] == 2
    assert summary["n_predicted"] == 1
    assert summary["coverage"] == pytest.approx(0.5)


def test_summarize_intersection_equals_own_when_all_covered():
    rows = [
        _make_row(post_id=1, gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=3.5, pred_emotion="JOY", pred_aspect="PRODUCT"),
        _make_row(post_id=2, gt_score=1.0, gt_emotion="ANGER", gt_aspect="BRAND",
                  pred_score=1.0, pred_emotion="ANGER", pred_aspect="BRAND"),
    ]
    # When the intersection equals the full set, own and intersection MAE match.
    summary = _summarize_one(_make_df(rows), intersection={1, 2})
    assert summary["score_mae_intersection"] == pytest.approx(summary["score_mae"])


def test_summarize_intersection_uses_only_intersection_rows():
    """A row outside the intersection must not affect intersection MAE."""
    rows = [
        _make_row(post_id=1, gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=4.0, pred_emotion="JOY", pred_aspect="PRODUCT"),  # perfect, in inter
        _make_row(post_id=2, gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=0.0, pred_emotion="JOY", pred_aspect="PRODUCT"),  # huge miss, out of inter
    ]
    summary = _summarize_one(_make_df(rows), intersection={1})
    assert summary["score_mae"] == pytest.approx(2.0)  # average of both
    assert summary["score_mae_intersection"] == pytest.approx(0.0)  # only post 1
    assert summary["intersection_n"] == 1


def test_compute_intersection_takes_set_intersection():
    a = _make_df([
        _make_row(post_id=1, gt_score=2.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=2.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
        _make_row(post_id=2, gt_score=2.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=2.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
    ])
    b = _make_df([
        _make_row(post_id=2, gt_score=2.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=2.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
        _make_row(post_id=3, gt_score=2.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=2.0, pred_emotion="JOY", pred_aspect="PRODUCT"),
    ])
    assert _compute_intersection({"a": a, "b": b}) == {2}


def test_summarize_end_to_end_writes_summary_files(tmp_path: Path):
    """`summarize()` should write summary.parquet + summary.csv and produce a
    non-empty pairwise table when there are >=2 experiments."""
    a = _make_df([
        _make_row(post_id=i, experiment_id="alpha",
                  gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=4.0, pred_emotion="JOY", pred_aspect="PRODUCT")
        for i in range(1, 6)
    ])
    b = _make_df([
        _make_row(post_id=i, experiment_id="beta",
                  gt_score=4.0, gt_emotion="JOY", gt_aspect="PRODUCT",
                  pred_score=3.0, pred_emotion="ANGER", pred_aspect="BRAND")
        for i in range(1, 6)
    ])
    a.to_parquet(tmp_path / "alpha.parquet", index=False)
    b.to_parquet(tmp_path / "beta.parquet", index=False)

    summary = summarize(tmp_path)
    assert (tmp_path / "summary.parquet").exists()
    assert (tmp_path / "summary.csv").exists()
    assert (tmp_path / "summary_pairwise.parquet").exists()

    # alpha is perfect on all dimensions; beta is consistently wrong.
    alpha = summary[summary.experiment_id == "alpha"].iloc[0]
    beta = summary[summary.experiment_id == "beta"].iloc[0]
    assert alpha["score_mae"] == pytest.approx(0.0)
    assert alpha["aspect_macro_f1"] == pytest.approx(1.0)
    assert beta["score_mae"] == pytest.approx(1.0)
    # Intersection size matches the overlap (all 5 rows).
    assert alpha["intersection_n"] == 5
    assert beta["intersection_n"] == 5

    pairwise = pd.read_parquet(tmp_path / "summary_pairwise.parquet")
    assert len(pairwise) == 1
    row = pairwise.iloc[0]
    # alpha - beta MAE diff is -1.0 (alpha better).
    assert row["mae_a_minus_b"] == pytest.approx(-1.0)
