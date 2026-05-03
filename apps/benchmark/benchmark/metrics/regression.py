"""Regression metrics for the 0-5 sentiment score."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np


@dataclass
class RegressionReport:
    mae: float
    rmse: float
    mean_error: float  # signed bias: positive = model overshoots
    pearson_r: float


def regression_report(
    y_true: Sequence[float], y_pred: Sequence[float]
) -> RegressionReport:
    if len(y_true) != len(y_pred):
        raise ValueError(f"length mismatch: {len(y_true)} vs {len(y_pred)}")
    if len(y_true) == 0:
        raise ValueError("cannot compute metrics over empty input")

    yt = np.asarray(y_true, dtype=np.float64)
    yp = np.asarray(y_pred, dtype=np.float64)
    err = yp - yt
    mae = float(np.mean(np.abs(err)))
    rmse = float(np.sqrt(np.mean(err**2)))
    mean_error = float(np.mean(err))

    # Pearson r is undefined when either side has zero variance. Report 0
    # rather than nan so downstream tables stay clean.
    if yt.std() == 0 or yp.std() == 0:
        pearson_r = 0.0
    else:
        pearson_r = float(np.corrcoef(yt, yp)[0, 1])

    return RegressionReport(
        mae=mae, rmse=rmse, mean_error=mean_error, pearson_r=pearson_r
    )
