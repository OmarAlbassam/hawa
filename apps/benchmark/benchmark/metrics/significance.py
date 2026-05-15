"""Paired statistical tests for comparing two models on the same posts.

For the paper's claims ("Model A beats Model B on emotion accuracy") you
want a paired test, not a single-number gap. Two functions here:

- `mcnemar_test`: classification — was A right where B was wrong, vs the
  reverse? p-value is for "the two models have the same error rate".
- `paired_bootstrap_diff`: works for any scalar metric (MAE, F1, accuracy).
  Returns (mean_difference, low_ci, high_ci, p_two_sided).

Both assume the same set of N items was scored by both models. Pass them
aligned arrays.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass

import numpy as np
from scipy.stats import binomtest


@dataclass
class McNemarResult:
    b: int  # A correct, B wrong
    c: int  # A wrong, B correct
    p_value: float
    odds_ratio: float | None  # b/c, None if c == 0


def mcnemar_test(
    a_correct: Sequence[bool], b_correct: Sequence[bool]
) -> McNemarResult:
    """Exact McNemar's test on paired correctness vectors.

    The exact (binomial) form is appropriate at the small scales we work at
    (<= a few hundred samples, b+c often < 25). Falls back to the binomial
    test on the discordant pairs (b, c) under p=0.5.
    """
    if len(a_correct) != len(b_correct):
        raise ValueError(f"length mismatch: {len(a_correct)} vs {len(b_correct)}")
    a = np.asarray(a_correct, dtype=bool)
    b = np.asarray(b_correct, dtype=bool)
    b_only = int(np.sum(a & ~b))  # A right, B wrong
    c_only = int(np.sum(~a & b))  # A wrong, B right
    discordant = b_only + c_only
    if discordant == 0:
        # Identical predictions → no evidence either way. Report p=1.
        return McNemarResult(b=0, c=0, p_value=1.0, odds_ratio=None)
    result = binomtest(min(b_only, c_only), discordant, p=0.5, alternative="two-sided")
    odds = (b_only / c_only) if c_only > 0 else None
    return McNemarResult(b=b_only, c=c_only, p_value=float(result.pvalue), odds_ratio=odds)


@dataclass
class BootstrapResult:
    mean_diff: float  # metric(A) - metric(B)
    ci_low: float
    ci_high: float
    p_two_sided: float


def paired_bootstrap_diff(
    a_values: Sequence[float],
    b_values: Sequence[float],
    metric: Callable[[np.ndarray], float],
    *,
    n_resamples: int = 1000,
    confidence: float = 0.95,
    rng_seed: int = 0,
) -> BootstrapResult:
    """Paired bootstrap for the difference in a scalar metric.

    `a_values` and `b_values` are the per-item values feeding the metric
    (e.g. absolute errors for MAE, or 0/1 correctness for accuracy). The
    metric callable receives a 1D numpy array and returns a scalar.

    The two-sided p-value approximates "is the difference zero?" by
    counting bootstrap resamples whose difference has the opposite sign of
    the observed difference.
    """
    if len(a_values) != len(b_values):
        raise ValueError(f"length mismatch: {len(a_values)} vs {len(b_values)}")
    n = len(a_values)
    if n == 0:
        raise ValueError("cannot bootstrap empty input")

    a = np.asarray(a_values, dtype=np.float64)
    b = np.asarray(b_values, dtype=np.float64)
    observed = metric(a) - metric(b)

    rng = np.random.default_rng(rng_seed)
    diffs = np.empty(n_resamples, dtype=np.float64)
    for i in range(n_resamples):
        idx = rng.integers(0, n, size=n)
        diffs[i] = metric(a[idx]) - metric(b[idx])

    alpha = 1 - confidence
    low = float(np.quantile(diffs, alpha / 2))
    high = float(np.quantile(diffs, 1 - alpha / 2))

    # Two-sided p: fraction of resamples whose sign disagrees with the
    # observed difference. This is the bootstrap analogue of an
    # achieved-significance level; cheap and adequate for the paper.
    if observed >= 0:
        p = float(np.mean(diffs <= 0))
    else:
        p = float(np.mean(diffs >= 0))
    p = min(1.0, 2.0 * p)

    return BootstrapResult(
        mean_diff=float(observed), ci_low=low, ci_high=high, p_two_sided=p
    )


def accuracy_metric(values: np.ndarray) -> float:
    return float(np.mean(values))


def mae_metric(values: np.ndarray) -> float:
    return float(np.mean(np.abs(values)))


@dataclass
class CIResult:
    point: float
    ci_low: float
    ci_high: float


def bootstrap_ci(
    values: Sequence[float],
    metric: Callable[[np.ndarray], float],
    *,
    n_resamples: int = 1000,
    confidence: float = 0.95,
    rng_seed: int = 0,
) -> CIResult:
    """Single-sample bootstrap confidence interval for a scalar metric.

    Resamples `values` with replacement `n_resamples` times, applies
    `metric` to each resample, and returns the percentile CI. Cheap
    enough to call once per experiment when building the headline table.

    Returns `(point=metric(values), ci_low, ci_high)`.
    """
    n = len(values)
    if n == 0:
        raise ValueError("cannot bootstrap empty input")
    arr = np.asarray(values, dtype=np.float64)
    point = float(metric(arr))
    rng = np.random.default_rng(rng_seed)
    stats = np.empty(n_resamples, dtype=np.float64)
    for i in range(n_resamples):
        idx = rng.integers(0, n, size=n)
        stats[i] = metric(arr[idx])
    alpha = 1 - confidence
    low = float(np.quantile(stats, alpha / 2))
    high = float(np.quantile(stats, 1 - alpha / 2))
    return CIResult(point=point, ci_low=low, ci_high=high)
