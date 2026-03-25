import pytest

from models import Emotion, Aspect
from services.analyzer import _normalize_result


def test_normalize_valid_result():
    raw = {
        "score": 4.2,
        "emotion": "JOY",
        "aspect": "PRODUCT",
    }
    result = _normalize_result(raw, post_id=1)
    assert result.post_id == 1
    assert result.score == 4.2
    assert result.llm_score == 4.2
    assert result.emotion == Emotion.JOY
    assert result.aspect == Aspect.PRODUCT


def test_normalize_clamps_score():
    raw = {
        "score": 7.0,
        "emotion": "ANGER",
        "aspect": "SERVICE",
    }
    result = _normalize_result(raw, post_id=2)
    assert result.score == 5.0


def test_normalize_clamps_negative_score():
    raw = {
        "score": -1.0,
        "emotion": "SADNESS",
        "aspect": "PRICING",
    }
    result = _normalize_result(raw, post_id=3)
    assert result.score == 0.0


def test_normalize_fixes_lowercase_enums():
    raw = {
        "score": 3.0,
        "emotion": "joy",
        "aspect": "delivery",
    }
    result = _normalize_result(raw, post_id=4)
    assert result.emotion == Emotion.JOY
    assert result.aspect == Aspect.DELIVERY


def test_normalize_missing_score_defaults():
    raw = {
        "emotion": "SURPRISE",
        "aspect": "SERVICE",
    }
    result = _normalize_result(raw, post_id=6)
    assert result.score == 2.5  # default neutral


def test_normalize_invalid_enum_raises():
    raw = {
        "score": 3.0,
        "emotion": "HAPPINESS",  # not a valid Emotion
        "aspect": "PRODUCT",
    }
    with pytest.raises(ValueError):
        _normalize_result(raw, post_id=7)
