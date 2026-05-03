"""Classification metrics for emotion and aspect labels."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_recall_fscore_support,
)


@dataclass
class ClassificationReport:
    accuracy: float
    macro_f1: float
    weighted_f1: float
    per_class: dict[str, dict[str, float]]  # label -> {precision, recall, f1, support}
    confusion: np.ndarray
    labels: tuple[str, ...]


def classification_report(
    y_true: Sequence[str],
    y_pred: Sequence[str],
    labels: Sequence[str],
) -> ClassificationReport:
    """Compute accuracy, macro-F1, weighted-F1, per-class P/R/F1, and confusion.

    `labels` fixes the order of classes in the confusion matrix and per-class
    output. Predictions outside the label set are kept as-is — they will not
    match any ground truth and will lower accuracy and the affected class's
    recall, which is the right behavior.
    """
    if len(y_true) != len(y_pred):
        raise ValueError(f"length mismatch: {len(y_true)} vs {len(y_pred)}")
    if len(y_true) == 0:
        raise ValueError("cannot compute metrics over empty input")

    labels = tuple(labels)
    accuracy = float(accuracy_score(y_true, y_pred))
    macro_f1 = float(f1_score(y_true, y_pred, labels=labels, average="macro", zero_division=0))
    weighted_f1 = float(
        f1_score(y_true, y_pred, labels=labels, average="weighted", zero_division=0)
    )

    precision, recall, f1, support = precision_recall_fscore_support(
        y_true, y_pred, labels=labels, zero_division=0
    )
    per_class = {
        lab: {
            "precision": float(precision[i]),
            "recall": float(recall[i]),
            "f1": float(f1[i]),
            "support": int(support[i]),
        }
        for i, lab in enumerate(labels)
    }

    confusion = confusion_matrix(y_true, y_pred, labels=labels)

    return ClassificationReport(
        accuracy=accuracy,
        macro_f1=macro_f1,
        weighted_f1=weighted_f1,
        per_class=per_class,
        confusion=confusion,
        labels=labels,
    )
