"""Aggregate per-experiment parquet outputs into a comparison report.

Computes summary metrics for each experiment and writes:
- `results/summary.parquet` — one row per experiment
- `results/<experiment_id>__confusion_emotion.png`
- `results/<experiment_id>__confusion_aspect.png`

The Streamlit viewer reads `summary.parquet` for the leaderboard and the
per-experiment parquets for drill-down.
"""

from __future__ import annotations

import logging
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns

from benchmark.dataset import ASPECT_LABELS, EMOTION_LABELS
from benchmark.metrics.aspect import OTHER
from benchmark.metrics.classification import classification_report
from benchmark.metrics.regression import regression_report

logger = logging.getLogger(__name__)


def summarize(results_dir: str | Path) -> pd.DataFrame:
    results_dir = Path(results_dir)
    rows: list[dict] = []
    for parquet_path in sorted(results_dir.glob("*.parquet")):
        if parquet_path.name == "summary.parquet":
            continue
        df = pd.read_parquet(parquet_path)
        rows.append(_summarize_one(df))
    summary = pd.DataFrame(rows)
    out_path = results_dir / "summary.parquet"
    summary.to_parquet(out_path, index=False)
    summary.to_csv(results_dir / "summary.csv", index=False)
    return summary


def _summarize_one(df: pd.DataFrame) -> dict:
    experiment_id = str(df["experiment_id"].iloc[0])
    n = len(df)
    n_errors = int(df["error"].notna().sum())
    relevant = df[(df["pred_relevant"] == True) & df["error"].isna()]  # noqa: E712

    # Score regression — only on rows where we got a numeric prediction.
    score_rows = relevant.dropna(subset=["pred_score"])
    if len(score_rows) > 0:
        reg = regression_report(score_rows["gt_score"], score_rows["pred_score"])
        mae, rmse, mean_err, pearson = reg.mae, reg.rmse, reg.mean_error, reg.pearson_r
    else:
        mae = rmse = mean_err = pearson = float("nan")

    # Emotion classification.
    emo_rows = relevant.dropna(subset=["pred_emotion"])
    if len(emo_rows) > 0:
        emo = classification_report(
            emo_rows["gt_emotion"], emo_rows["pred_emotion"], EMOTION_LABELS
        )
        emo_acc, emo_macro_f1 = emo.accuracy, emo.macro_f1
    else:
        emo_acc = emo_macro_f1 = float("nan")

    # Aspect classification (use normalized aspect).
    asp_rows = relevant.dropna(subset=["pred_aspect_normalized"])
    asp_labels = (*ASPECT_LABELS, OTHER)
    if len(asp_rows) > 0:
        asp = classification_report(
            asp_rows["gt_aspect"], asp_rows["pred_aspect_normalized"], asp_labels
        )
        asp_acc, asp_macro_f1 = asp.accuracy, asp.macro_f1
    else:
        asp_acc = asp_macro_f1 = float("nan")

    latency_p50 = float(np.nanmedian(df["latency_ms"])) if df["latency_ms"].notna().any() else float("nan")
    latency_p95 = (
        float(np.nanpercentile(df["latency_ms"].dropna(), 95))
        if df["latency_ms"].notna().any()
        else float("nan")
    )

    # Token usage — server-reported per-request counts. Older parquet files
    # written before token capture landed won't have these columns; treat as
    # absent gracefully.
    if "total_tokens" in df.columns and df["total_tokens"].notna().any():
        total_tokens = int(df["total_tokens"].sum(skipna=True))
        mean_tokens = float(df["total_tokens"].mean(skipna=True))
        tokens_p50 = float(np.nanmedian(df["total_tokens"]))
        tokens_p95 = float(np.nanpercentile(df["total_tokens"].dropna(), 95))
        prompt_tokens_total = int(df["prompt_tokens"].sum(skipna=True))
        completion_tokens_total = int(df["completion_tokens"].sum(skipna=True))
    else:
        total_tokens = 0
        mean_tokens = tokens_p50 = tokens_p95 = float("nan")
        prompt_tokens_total = completion_tokens_total = 0

    return {
        "experiment_id": experiment_id,
        "n": n,
        "n_errors": n_errors,
        "n_predicted": int(len(relevant)),
        "score_mae": mae,
        "score_rmse": rmse,
        "score_mean_error": mean_err,
        "score_pearson_r": pearson,
        "emotion_accuracy": emo_acc,
        "emotion_macro_f1": emo_macro_f1,
        "aspect_accuracy": asp_acc,
        "aspect_macro_f1": asp_macro_f1,
        "total_tokens": total_tokens,
        "prompt_tokens_total": prompt_tokens_total,
        "completion_tokens_total": completion_tokens_total,
        "mean_tokens": mean_tokens,
        "tokens_p50": tokens_p50,
        "tokens_p95": tokens_p95,
        "latency_ms_p50": latency_p50,
        "latency_ms_p95": latency_p95,
    }


def plot_confusion(
    df: pd.DataFrame,
    *,
    label_col: str,
    pred_col: str,
    labels: tuple[str, ...],
    out_path: str | Path,
    title: str,
) -> None:
    rows = df.dropna(subset=[pred_col])
    if len(rows) == 0:
        return
    report = classification_report(rows[label_col], rows[pred_col], labels)
    fig, ax = plt.subplots(figsize=(max(6, len(labels) * 0.7), max(5, len(labels) * 0.6)))
    sns.heatmap(
        report.confusion,
        xticklabels=labels,
        yticklabels=labels,
        annot=True,
        fmt="d",
        cbar=False,
        ax=ax,
        cmap="Blues",
    )
    ax.set_xlabel("predicted")
    ax.set_ylabel("ground truth")
    ax.set_title(title)
    fig.tight_layout()
    fig.savefig(out_path, dpi=120)
    plt.close(fig)


def plot_all_confusions(results_dir: str | Path) -> None:
    results_dir = Path(results_dir)
    asp_labels = (*ASPECT_LABELS, OTHER)
    for parquet_path in sorted(results_dir.glob("*.parquet")):
        if parquet_path.name == "summary.parquet":
            continue
        df = pd.read_parquet(parquet_path)
        rel = df[(df["pred_relevant"] == True) & df["error"].isna()]  # noqa: E712
        if rel.empty:
            continue
        exp_id = parquet_path.stem
        plot_confusion(
            rel,
            label_col="gt_emotion",
            pred_col="pred_emotion",
            labels=EMOTION_LABELS,
            out_path=results_dir / f"{exp_id}__confusion_emotion.png",
            title=f"{exp_id}: emotion",
        )
        plot_confusion(
            rel,
            label_col="gt_aspect",
            pred_col="pred_aspect_normalized",
            labels=asp_labels,
            out_path=results_dir / f"{exp_id}__confusion_aspect.png",
            title=f"{exp_id}: aspect",
        )
