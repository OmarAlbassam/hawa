"""Aggregate per-experiment parquet outputs into a comparison report.

Computes summary metrics for each experiment and writes:
- `results/summary.parquet` — one row per experiment
- `results/summary_pairwise.parquet` — paired McNemar / bootstrap diffs across experiment pairs
- `results/<experiment_id>__confusion_emotion.png`
- `results/<experiment_id>__confusion_aspect.png`

The Streamlit viewer reads `summary.parquet` for the leaderboard and the
per-experiment parquets for drill-down.

Metric definitions (read this before quoting numbers):

- ``score_mae`` / ``emotion_macro_f1`` / ``aspect_macro_f1`` are computed
  on **each experiment's own relevant subset** — the rows where the
  model produced a non-abstaining prediction. Models that abstain
  aggressively get scored on a smaller (often easier) sample.
- ``*_intersection`` variants compute the same metrics on the
  **intersection set** — posts every experiment in the results dir
  scored as relevant. This is the apples-to-apples comparison number.
- ``coverage`` is the fraction of input posts the experiment produced a
  relevant prediction for. A model that abstains on hard cases will
  have a lower ``coverage`` but possibly a better ``score_mae``.
- Macro-F1 is computed only over GT-present classes (e.g. if the
  dataset has no ``DELIVERY`` rows, ``DELIVERY`` is not included in the
  macro mean — averaging over zero-support classes is meaningless).
- ``score_mae_ci95_low`` / ``score_mae_ci95_high`` are 95% percentile
  bootstrap CIs on the per-experiment MAE; useful when comparing two
  point estimates that are within a few hundredths of each other.
"""

from __future__ import annotations

import logging
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns

from benchmark.dataset import ASPECT_LABELS, EMOTION_LABELS, load_dataset
from benchmark.metrics.classification import classification_report
from benchmark.metrics.regression import regression_report
from benchmark.metrics.significance import (
    accuracy_metric,
    bootstrap_ci,
    mae_metric,
    mcnemar_test,
    paired_bootstrap_diff,
)

logger = logging.getLogger(__name__)


def sync_gt(results_dir: str | Path, data_path: str | Path) -> dict[str, int]:
    """Refresh GT columns on every per-experiment parquet from the dataset.

    Useful after relabeling `control.jsonl` (relevance flags, score
    corrections) without re-running the experiments. The model
    predictions don't change — only the ground truth does — so we can
    patch the parquets in place rather than burning LLM calls.

    For every post_id in the parquet that exists in `data_path`,
    overwrites `gt_score`, `gt_emotion`, `gt_aspect` and attaches
    `gt_is_relevant` / `irrelevance_reason` columns.

    Returns a dict: {experiment_id: n_rows_updated}.
    """
    results_dir = Path(results_dir)
    samples = load_dataset(data_path)
    gt_by_id: dict[int, dict] = {}
    # Also read the raw JSONL to recover the optional `irrelevance_reason`
    # (the dataset loader doesn't preserve it).
    import json as _json
    with Path(data_path).open() as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = _json.loads(line)
            gt_by_id[int(row["post_id"])] = {
                "gt_score": float(row["gt_score"]),
                "gt_emotion": str(row["gt_emotion"]).upper(),
                "gt_aspect": str(row["gt_aspect"]).upper(),
                "gt_is_relevant": bool(row.get("gt_is_relevant", True)),
                "irrelevance_reason": row.get("irrelevance_reason"),
            }
    # Sanity-check loader and raw parser agree on relevance counts.
    n_irrel_loader = sum(1 for s in samples if not s.gt.is_relevant)
    n_irrel_raw = sum(1 for v in gt_by_id.values() if not v["gt_is_relevant"])
    if n_irrel_loader != n_irrel_raw:
        raise RuntimeError(
            f"dataset loader and raw parser disagree on gt_is_relevant counts: "
            f"{n_irrel_loader} vs {n_irrel_raw}"
        )

    updates: dict[str, int] = {}
    for parquet_path in sorted(results_dir.glob("*.parquet")):
        if parquet_path.name in {"summary.parquet", "summary_pairwise.parquet"}:
            continue
        df = pd.read_parquet(parquet_path)
        n_changed = 0
        for col in ("gt_score", "gt_emotion", "gt_aspect"):
            new_values = df["post_id"].map(lambda pid: gt_by_id.get(int(pid), {}).get(col))
            # Count cells that actually flip.
            n_changed += int((df[col].astype(object) != new_values.astype(object)).sum())
            df[col] = new_values
        df["gt_is_relevant"] = df["post_id"].map(
            lambda pid: gt_by_id.get(int(pid), {}).get("gt_is_relevant", True)
        )
        df["irrelevance_reason"] = df["post_id"].map(
            lambda pid: gt_by_id.get(int(pid), {}).get("irrelevance_reason")
        )
        df.to_parquet(parquet_path, index=False)
        updates[parquet_path.stem] = n_changed
        logger.info("synced %s (%d gt cells updated)", parquet_path.stem, n_changed)
    return updates


def _present_labels(series: pd.Series, taxonomy: tuple[str, ...]) -> tuple[str, ...]:
    """Return the subset of `taxonomy` actually represented in `series`.

    Macro-F1 averages over classes; zero-support classes contribute F1=0
    and pull the mean down for no reason. Restricting to GT-present
    classes makes the average meaningful.
    """
    present = set(series.dropna().unique())
    return tuple(lab for lab in taxonomy if lab in present)


def _relevant_rows(df: pd.DataFrame) -> pd.DataFrame:
    """Rows eligible for score/emotion/aspect grading.

    Filters on both prediction-relevance (the model said it had labels
    to give) AND ground-truth-relevance (we have valid labels to grade
    against). The latter is only available when `gt_is_relevant` is on
    the frame; for older parquets without that column, all rows are
    treated as relevant (the historical default).
    """
    mask = (df["pred_relevant"] == True) & df["error"].isna()  # noqa: E712
    if "gt_is_relevant" in df.columns:
        # NaN/missing → treat as relevant.
        mask &= df["gt_is_relevant"].fillna(True).astype(bool)
    return df[mask]


def _compute_intersection(runs: dict[str, pd.DataFrame]) -> set[int]:
    """post_ids every experiment scored as relevant + numeric."""
    if not runs:
        return set()
    sets: list[set[int]] = []
    for df in runs.values():
        rel = _relevant_rows(df).dropna(subset=["pred_score"])
        sets.append(set(rel["post_id"].tolist()))
    return set.intersection(*sets) if sets else set()


def summarize(results_dir: str | Path) -> pd.DataFrame:
    results_dir = Path(results_dir)
    runs: dict[str, pd.DataFrame] = {}
    for parquet_path in sorted(results_dir.glob("*.parquet")):
        if parquet_path.name in {"summary.parquet", "summary_pairwise.parquet"}:
            continue
        runs[parquet_path.stem] = pd.read_parquet(parquet_path)

    intersection = _compute_intersection(runs)
    logger.info("intersection set size: %d", len(intersection))

    rows: list[dict] = [
        _summarize_one(df, intersection=intersection) for df in runs.values()
    ]
    summary = pd.DataFrame(rows)
    summary.to_parquet(results_dir / "summary.parquet", index=False)
    summary.to_csv(results_dir / "summary.csv", index=False)

    pairwise = _summarize_pairwise(runs, intersection=intersection)
    if not pairwise.empty:
        pairwise.to_parquet(results_dir / "summary_pairwise.parquet", index=False)
        pairwise.to_csv(results_dir / "summary_pairwise.csv", index=False)

    return summary


def _summarize_one(df: pd.DataFrame, *, intersection: set[int]) -> dict:
    experiment_id = str(df["experiment_id"].iloc[0])
    n = len(df)
    n_errors = int(df["error"].notna().sum())
    relevant = _relevant_rows(df)

    # Score regression — only on rows where we got a numeric prediction.
    score_rows = relevant.dropna(subset=["pred_score"])
    if len(score_rows) > 0:
        reg = regression_report(score_rows["gt_score"], score_rows["pred_score"])
        mae, rmse, mean_err, pearson = reg.mae, reg.rmse, reg.mean_error, reg.pearson_r
        # 95% percentile bootstrap CI on MAE.
        err_values = (score_rows["pred_score"].to_numpy() - score_rows["gt_score"].to_numpy())
        ci = bootstrap_ci(err_values, mae_metric, n_resamples=1000)
        mae_ci_low, mae_ci_high = ci.ci_low, ci.ci_high
    else:
        mae = rmse = mean_err = pearson = float("nan")
        mae_ci_low = mae_ci_high = float("nan")

    # Emotion classification — restrict macro-F1 to GT-present classes.
    emo_rows = relevant.dropna(subset=["pred_emotion"])
    if len(emo_rows) > 0:
        emo_labels = _present_labels(emo_rows["gt_emotion"], EMOTION_LABELS)
        emo = classification_report(
            emo_rows["gt_emotion"], emo_rows["pred_emotion"], emo_labels
        )
        emo_acc, emo_macro_f1 = emo.accuracy, emo.macro_f1
    else:
        emo_acc = emo_macro_f1 = float("nan")

    # Aspect classification — restrict macro-F1 to GT-present classes.
    # ASPECT_LABELS already includes OTHER; we don't extend it (that was
    # the source of the historical double-counting bug).
    asp_rows = relevant.dropna(subset=["pred_aspect_normalized"])
    if len(asp_rows) > 0:
        asp_labels = _present_labels(asp_rows["gt_aspect"], ASPECT_LABELS)
        asp = classification_report(
            asp_rows["gt_aspect"], asp_rows["pred_aspect_normalized"], asp_labels
        )
        asp_acc, asp_macro_f1 = asp.accuracy, asp.macro_f1
    else:
        asp_acc = asp_macro_f1 = float("nan")

    # Intersection metrics — same definitions, restricted to the set of
    # post_ids every experiment scored. Apples-to-apples comparison.
    inter_score_mae = inter_emo_f1 = inter_asp_f1 = float("nan")
    inter_n = 0
    if intersection:
        inter = df[df["post_id"].isin(intersection)]
        inter_rel = _relevant_rows(inter)
        inter_score = inter_rel.dropna(subset=["pred_score"])
        inter_n = len(inter_score)
        if inter_n > 0:
            inter_score_mae = float(
                np.mean(np.abs(inter_score["pred_score"] - inter_score["gt_score"]))
            )
        inter_emo = inter_rel.dropna(subset=["pred_emotion"])
        if len(inter_emo) > 0:
            labs = _present_labels(inter_emo["gt_emotion"], EMOTION_LABELS)
            inter_emo_f1 = classification_report(
                inter_emo["gt_emotion"], inter_emo["pred_emotion"], labs
            ).macro_f1
        inter_asp = inter_rel.dropna(subset=["pred_aspect_normalized"])
        if len(inter_asp) > 0:
            labs = _present_labels(inter_asp["gt_aspect"], ASPECT_LABELS)
            inter_asp_f1 = classification_report(
                inter_asp["gt_aspect"], inter_asp["pred_aspect_normalized"], labs
            ).macro_f1

    coverage = float(len(relevant) / n) if n else float("nan")

    # Relevance classification: model's pred_relevant vs gt_is_relevant.
    # Only available when the parquet has been sync'd against an annotated
    # dataset (older parquets won't have `gt_is_relevant`).
    rel_acc = rel_f1 = rel_recall_irr = rel_precision_irr = float("nan")
    n_gt_irrelevant = 0
    if "gt_is_relevant" in df.columns and df["gt_is_relevant"].notna().any():
        scored = df[df["pred_relevant"].notna() & df["gt_is_relevant"].notna()]
        if len(scored) > 0:
            gt = scored["gt_is_relevant"].astype(bool).to_numpy()
            pr = scored["pred_relevant"].astype(bool).to_numpy()
            n_gt_irrelevant = int((~gt).sum())
            rel_acc = float(np.mean(gt == pr))
            # F1 of the "is relevant" class — that's the operative decision.
            tp = int(((gt == True) & (pr == True)).sum())  # noqa: E712
            fp = int(((gt == False) & (pr == True)).sum())  # noqa: E712
            fn = int(((gt == True) & (pr == False)).sum())  # noqa: E712
            tn = int(((gt == False) & (pr == False)).sum())  # noqa: E712
            prec = tp / (tp + fp) if (tp + fp) else 0.0
            rec = tp / (tp + fn) if (tp + fn) else 0.0
            rel_f1 = (2 * prec * rec / (prec + rec)) if (prec + rec) else 0.0
            # Recall/precision on the rarer "irrelevant" class: how good is
            # the model at *catching* spam/homonym/etc?
            rel_recall_irr = tn / (tn + fp) if (tn + fp) else float("nan")
            rel_precision_irr = tn / (tn + fn) if (tn + fn) else float("nan")

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
        "coverage": coverage,
        "score_mae": mae,
        "score_mae_ci95_low": mae_ci_low,
        "score_mae_ci95_high": mae_ci_high,
        "score_rmse": rmse,
        "score_mean_error": mean_err,
        "score_pearson_r": pearson,
        "emotion_accuracy": emo_acc,
        "emotion_macro_f1": emo_macro_f1,
        "aspect_accuracy": asp_acc,
        "aspect_macro_f1": asp_macro_f1,
        "intersection_n": inter_n,
        "score_mae_intersection": inter_score_mae,
        "emotion_macro_f1_intersection": inter_emo_f1,
        "aspect_macro_f1_intersection": inter_asp_f1,
        "n_gt_irrelevant": n_gt_irrelevant,
        "relevance_accuracy": rel_acc,
        "relevance_f1": rel_f1,
        "relevance_recall_irrelevant": rel_recall_irr,
        "relevance_precision_irrelevant": rel_precision_irr,
        "total_tokens": total_tokens,
        "prompt_tokens_total": prompt_tokens_total,
        "completion_tokens_total": completion_tokens_total,
        "mean_tokens": mean_tokens,
        "tokens_p50": tokens_p50,
        "tokens_p95": tokens_p95,
        "latency_ms_p50": latency_p50,
        "latency_ms_p95": latency_p95,
    }


def _summarize_pairwise(
    runs: dict[str, pd.DataFrame], *, intersection: set[int]
) -> pd.DataFrame:
    """Paired McNemar + bootstrap diffs for every (A, B) experiment pair.

    Computed on the intersection set so the paired tests are valid
    (same items scored by both models). Cheap; the inner loops are O(n)
    over a few hundred posts.
    """
    if not intersection or len(runs) < 2:
        return pd.DataFrame()

    inter_ids = sorted(intersection)
    # Build aligned per-experiment frames indexed by post_id.
    aligned: dict[str, pd.DataFrame] = {}
    for eid, df in runs.items():
        sub = df[df["post_id"].isin(intersection)].set_index("post_id").loc[inter_ids]
        aligned[eid] = sub

    rows: list[dict] = []
    experiment_ids = list(runs.keys())
    for i, a in enumerate(experiment_ids):
        for b in experiment_ids[i + 1 :]:
            da, db = aligned[a], aligned[b]
            # MAE bootstrap on per-row absolute errors.
            err_a = (da["pred_score"] - da["gt_score"]).to_numpy()
            err_b = (db["pred_score"] - db["gt_score"]).to_numpy()
            mae_diff = paired_bootstrap_diff(err_a, err_b, mae_metric, n_resamples=1000)
            # Emotion accuracy McNemar.
            emo_a = (da["pred_emotion"] == da["gt_emotion"]).to_numpy()
            emo_b = (db["pred_emotion"] == db["gt_emotion"]).to_numpy()
            mc = mcnemar_test(emo_a.tolist(), emo_b.tolist())
            # Emotion accuracy bootstrap on 0/1 correctness — also gives a CI on the gap.
            acc_diff = paired_bootstrap_diff(
                emo_a.astype(float), emo_b.astype(float), accuracy_metric, n_resamples=1000
            )
            rows.append(
                {
                    "experiment_a": a,
                    "experiment_b": b,
                    "n": len(inter_ids),
                    "mae_a_minus_b": mae_diff.mean_diff,
                    "mae_diff_ci95_low": mae_diff.ci_low,
                    "mae_diff_ci95_high": mae_diff.ci_high,
                    "mae_diff_p_two_sided": mae_diff.p_two_sided,
                    "emotion_acc_a_minus_b": acc_diff.mean_diff,
                    "emotion_acc_diff_ci95_low": acc_diff.ci_low,
                    "emotion_acc_diff_ci95_high": acc_diff.ci_high,
                    "emotion_mcnemar_b": mc.b,
                    "emotion_mcnemar_c": mc.c,
                    "emotion_mcnemar_p_value": mc.p_value,
                }
            )
    return pd.DataFrame(rows)


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
    for parquet_path in sorted(results_dir.glob("*.parquet")):
        if parquet_path.name in {"summary.parquet", "summary_pairwise.parquet"}:
            continue
        df = pd.read_parquet(parquet_path)
        rel = _relevant_rows(df)
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
            labels=ASPECT_LABELS,
            out_path=results_dir / f"{exp_id}__confusion_aspect.png",
            title=f"{exp_id}: aspect",
        )
