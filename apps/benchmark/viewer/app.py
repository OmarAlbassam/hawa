"""Streamlit viewer for benchmark results.

Run: `streamlit run viewer/app.py`

Three tabs:
1. Leaderboard — every experiment's headline metrics, sortable.
2. Confusion matrices — per-experiment emotion + aspect heatmaps.
3. Per-post diff — pick two experiments, inspect rows where they disagree.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import plotly.express as px
import streamlit as st

# Allow `viewer/app.py` to import the `benchmark` package without needing
# `pip install -e .`. Streamlit users often run from a venv that hasn't been
# editable-installed, so this shims the path.
_THIS = Path(__file__).resolve()
sys.path.insert(0, str(_THIS.parent.parent))

from benchmark.dataset import ASPECT_LABELS, EMOTION_LABELS  # noqa: E402
from benchmark.metrics.aspect import OTHER  # noqa: E402
from benchmark.metrics.classification import classification_report  # noqa: E402
from benchmark.metrics.significance import (  # noqa: E402
    accuracy_metric, mae_metric, mcnemar_test, paired_bootstrap_diff,
)

st.set_page_config(page_title="Hawa benchmark", layout="wide")

RESULTS_DIR = Path(os.environ.get("BENCHMARK_RESULTS_DIR", "results"))


@st.cache_data
def load_summary(results_dir: Path) -> pd.DataFrame:
    path = results_dir / "summary.parquet"
    if not path.exists():
        return pd.DataFrame()
    return pd.read_parquet(path)


@st.cache_data
def load_experiment(parquet_path: Path) -> pd.DataFrame:
    return pd.read_parquet(parquet_path)


def main() -> None:
    st.title("Hawa LLM benchmark")
    summary = load_summary(RESULTS_DIR)

    if summary.empty:
        st.warning(f"no summary at {RESULTS_DIR / 'summary.parquet'} — run `benchmark report` first.")
        return

    tab1, tab2, tab3 = st.tabs(["Leaderboard", "Confusion matrices", "Pairwise comparison"])
    with tab1:
        _render_leaderboard(summary)
    with tab2:
        _render_confusions(summary)
    with tab3:
        _render_pairwise(summary)


def _render_leaderboard(summary: pd.DataFrame) -> None:
    st.subheader("Leaderboard")
    st.caption("Sort by any metric. Lower is better for MAE/RMSE; higher is better for everything else.")
    st.dataframe(
        summary.sort_values("emotion_macro_f1", ascending=False, na_position="last"),
        use_container_width=True,
        hide_index=True,
    )

    if "score_mae" in summary.columns and summary["score_mae"].notna().any():
        st.subheader("MAE on sentiment score (0-5)")
        fig = px.bar(
            summary.dropna(subset=["score_mae"]).sort_values("score_mae"),
            x="experiment_id", y="score_mae",
        )
        st.plotly_chart(fig, use_container_width=True)


def _render_confusions(summary: pd.DataFrame) -> None:
    st.subheader("Confusion matrices")
    exp_ids = summary["experiment_id"].tolist()
    if not exp_ids:
        return
    chosen = st.selectbox("experiment", exp_ids, key="conf_exp")
    df = load_experiment(RESULTS_DIR / f"{chosen}.parquet")
    rel = df[(df["pred_relevant"] == True) & df["error"].isna()]  # noqa: E712
    if rel.empty:
        st.info("no relevant predictions in this experiment")
        return

    col1, col2 = st.columns(2)
    with col1:
        _confusion_heatmap(rel, "gt_emotion", "pred_emotion", EMOTION_LABELS, "Emotion")
    with col2:
        asp_labels = (*ASPECT_LABELS, OTHER)
        _confusion_heatmap(rel, "gt_aspect", "pred_aspect_normalized", asp_labels, "Aspect")


def _confusion_heatmap(
    df: pd.DataFrame, gt_col: str, pred_col: str, labels: tuple[str, ...], title: str
) -> None:
    rows = df.dropna(subset=[pred_col])
    if rows.empty:
        st.info(f"no predictions for {title.lower()}")
        return
    report = classification_report(rows[gt_col], rows[pred_col], labels)
    fig = px.imshow(
        report.confusion,
        x=list(labels), y=list(labels),
        text_auto=True, aspect="auto", color_continuous_scale="Blues",
        labels=dict(x="predicted", y="ground truth"),
    )
    fig.update_layout(title=f"{title} — accuracy {report.accuracy:.3f}, macro-F1 {report.macro_f1:.3f}")
    st.plotly_chart(fig, use_container_width=True)


def _render_pairwise(summary: pd.DataFrame) -> None:
    st.subheader("Pairwise comparison")
    st.caption("McNemar for classification, paired bootstrap for MAE. Both require both experiments to have scored the same posts.")
    exp_ids = summary["experiment_id"].tolist()
    if len(exp_ids) < 2:
        st.info("need at least two experiments")
        return
    a = st.selectbox("experiment A", exp_ids, key="pair_a")
    b = st.selectbox("experiment B", [e for e in exp_ids if e != a], key="pair_b")

    da = load_experiment(RESULTS_DIR / f"{a}.parquet")
    db = load_experiment(RESULTS_DIR / f"{b}.parquet")
    aligned = da.merge(db, on="post_id", suffixes=("_a", "_b"))
    aligned = aligned[
        (aligned["pred_relevant_a"] == True)  # noqa: E712
        & (aligned["pred_relevant_b"] == True)  # noqa: E712
        & aligned["error_a"].isna() & aligned["error_b"].isna()
    ]
    if aligned.empty:
        st.warning("no posts where both experiments produced relevant predictions")
        return

    st.write(f"**{len(aligned)} posts** in common (after filtering errors and irrelevant).")

    a_emo = aligned["pred_emotion_a"] == aligned["gt_emotion_a"]
    b_emo = aligned["pred_emotion_b"] == aligned["gt_emotion_b"]
    mc = mcnemar_test(a_emo.tolist(), b_emo.tolist())
    st.write(
        f"**Emotion**: A correct/B wrong = {mc.b}, A wrong/B correct = {mc.c}, "
        f"McNemar p = {mc.p_value:.4f}"
    )

    if aligned["pred_score_a"].notna().any() and aligned["pred_score_b"].notna().any():
        a_err = np.abs(aligned["pred_score_a"] - aligned["gt_score_a"]).to_numpy()
        b_err = np.abs(aligned["pred_score_b"] - aligned["gt_score_b"]).to_numpy()
        boot = paired_bootstrap_diff(a_err, b_err, mae_metric, n_resamples=2000)
        st.write(
            f"**Score MAE (A − B)**: {boot.mean_diff:+.3f} "
            f"(95% CI [{boot.ci_low:+.3f}, {boot.ci_high:+.3f}], p={boot.p_two_sided:.4f})"
        )

    a_acc = (aligned["pred_emotion_a"] == aligned["gt_emotion_a"]).astype(float).to_numpy()
    b_acc = (aligned["pred_emotion_b"] == aligned["gt_emotion_b"]).astype(float).to_numpy()
    boot_acc = paired_bootstrap_diff(a_acc, b_acc, accuracy_metric, n_resamples=2000)
    st.write(
        f"**Emotion accuracy (A − B)**: {boot_acc.mean_diff:+.3f} "
        f"(95% CI [{boot_acc.ci_low:+.3f}, {boot_acc.ci_high:+.3f}], p={boot_acc.p_two_sided:.4f})"
    )

    disagree = aligned[
        (aligned["pred_emotion_a"] != aligned["pred_emotion_b"])
        | (aligned["pred_aspect_normalized_a"] != aligned["pred_aspect_normalized_b"])
    ]
    st.subheader(f"Disagreements ({len(disagree)} posts)")
    cols = [
        "post_id",
        "gt_score_a", "pred_score_a", "pred_score_b",
        "gt_emotion_a", "pred_emotion_a", "pred_emotion_b",
        "gt_aspect_a", "pred_aspect_normalized_a", "pred_aspect_normalized_b",
    ]
    available = [c for c in cols if c in disagree.columns]
    st.dataframe(disagree[available], use_container_width=True, hide_index=True)


if __name__ == "__main__":
    main()
