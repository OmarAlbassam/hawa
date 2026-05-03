from __future__ import annotations

import numpy as np
import pytest

from benchmark.metrics.aspect import OTHER, normalize_aspect
from benchmark.metrics.classification import classification_report
from benchmark.metrics.regression import regression_report
from benchmark.metrics.significance import (
    accuracy_metric,
    mae_metric,
    mcnemar_test,
    paired_bootstrap_diff,
)


def test_classification_perfect_predictions():
    labels = ("A", "B", "C")
    y_true = ["A", "B", "C", "A", "B"]
    y_pred = ["A", "B", "C", "A", "B"]
    r = classification_report(y_true, y_pred, labels)
    assert r.accuracy == 1.0
    assert r.macro_f1 == 1.0
    assert all(c["f1"] == 1.0 for c in r.per_class.values())


def test_classification_handles_unknown_predictions():
    # Predictions outside the label set should not crash and should reduce
    # accuracy / the affected class's recall.
    labels = ("A", "B")
    r = classification_report(["A", "A", "B"], ["A", "C", "B"], labels)
    assert r.accuracy == pytest.approx(2 / 3)


def test_regression_basic():
    r = regression_report([0.0, 1.0, 2.0], [0.5, 1.0, 1.5])
    assert r.mae == pytest.approx(1 / 3)
    assert r.rmse == pytest.approx(((0.25 + 0 + 0.25) / 3) ** 0.5)
    assert r.mean_error == pytest.approx(0.0)


def test_regression_zero_variance_returns_zero_pearson():
    r = regression_report([1.0, 1.0, 1.0], [2.0, 2.0, 2.0])
    assert r.pearson_r == 0.0


def test_normalize_aspect_canonical_passthrough():
    assert normalize_aspect("PRODUCT") == "PRODUCT"
    assert normalize_aspect("product") == "PRODUCT"
    assert normalize_aspect(" Service ") == "SERVICE"


def test_normalize_aspect_synonyms():
    assert normalize_aspect("shipping") == "DELIVERY"
    assert normalize_aspect("customer service") == "SERVICE"
    assert normalize_aspect("value for money") == "PRICING"


def test_normalize_aspect_unknown_falls_to_other():
    assert normalize_aspect("ambiance") == OTHER
    assert normalize_aspect(None) == OTHER
    assert normalize_aspect("") == OTHER


def test_mcnemar_identical_predictions_returns_p_one():
    r = mcnemar_test([True, False, True], [True, False, True])
    assert r.b == 0 and r.c == 0
    assert r.p_value == 1.0


def test_mcnemar_strong_disagreement_significant():
    # A correct on every item, B wrong on every item: 10 discordant b-only,
    # 0 c-only — the binomial test should produce a small p.
    a = [True] * 10
    b = [False] * 10
    r = mcnemar_test(a, b)
    assert r.b == 10 and r.c == 0
    assert r.p_value < 0.01


def test_paired_bootstrap_zero_when_models_identical():
    rng = np.random.default_rng(42)
    a = rng.uniform(0, 1, size=50)
    b = a.copy()
    r = paired_bootstrap_diff(a, b, accuracy_metric, n_resamples=200)
    assert r.mean_diff == 0.0
    assert r.ci_low <= 0.0 <= r.ci_high


def test_paired_bootstrap_detects_consistent_advantage():
    # A's "errors" are all 0.1, B's are all 0.5 → MAE(A) - MAE(B) = -0.4
    a = np.full(30, 0.1)
    b = np.full(30, 0.5)
    r = paired_bootstrap_diff(a, b, mae_metric, n_resamples=500)
    assert r.mean_diff == pytest.approx(-0.4)
    assert r.ci_high < 0
    assert r.p_two_sided < 0.05
