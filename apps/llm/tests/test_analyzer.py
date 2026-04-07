import pytest
from pydantic import ValidationError

from models import Emotion, SentimentResponse


def test_valid_response():
    r = SentimentResponse(score=4.2, emotion="JOY", aspect="PRODUCT")
    assert r.score == 4.2
    assert r.emotion == Emotion.JOY
    assert r.aspect == "PRODUCT"


def test_clamps_high_score():
    r = SentimentResponse(score=7.0, emotion="ANGER", aspect="SERVICE")
    assert r.score == 5.0


def test_clamps_negative_score():
    r = SentimentResponse(score=-1.0, emotion="SADNESS", aspect="PRICING")
    assert r.score == 0.0


def test_default_score():
    r = SentimentResponse(emotion="SURPRISE", aspect="SERVICE")
    assert r.score == 2.5


def test_uppercase_emotion():
    r = SentimentResponse(score=3.0, emotion="joy", aspect="delivery")
    assert r.emotion == Emotion.JOY


def test_invalid_emotion_raises():
    with pytest.raises(ValidationError):
        SentimentResponse(score=3.0, emotion="HAPPINESS", aspect="PRODUCT")


def test_freeform_aspect():
    r = SentimentResponse(score=3.0, emotion="JOY", aspect="customer support")
    assert r.aspect == "customer support"
