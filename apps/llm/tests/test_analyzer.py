from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from config import Settings
from models import (
    AnalyzeRequest,
    AnalyzeResult,
    Emotion,
    IrrelevanceReason,
    SentimentResponse,
)
from services.analyzer import AnalyzerService


# --- SentimentResponse ---


def test_valid_half_step_response_passes_through():
    r = SentimentResponse(score=4.0, emotion="JOY", aspect="PRODUCT")
    assert r.is_relevant is True
    assert r.irrelevance_reason is None
    assert r.score == 4.0
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


@pytest.mark.parametrize(
    "raw,expected",
    [
        (3.0, 3.0),
        (3.5, 3.5),
        (4.2, 4.0),
        (3.7, 3.5),
        (3.8, 4.0),
        (2.74, 2.5),
        (4.99, 5.0),
        (0.24, 0.0),
        (0.25, 0.5),
    ],
)
def test_snaps_score_to_nearest_half_step(raw: float, expected: float):
    r = SentimentResponse(score=raw, emotion="NEUTRAL", aspect="BRAND")
    assert r.score == expected
    assert (r.score * 2).is_integer()
    assert 0.0 <= r.score <= 5.0


def test_uppercase_emotion():
    r = SentimentResponse(score=3.0, emotion="joy", aspect="delivery")
    assert r.emotion == Emotion.JOY


def test_invalid_emotion_raises():
    with pytest.raises(ValidationError):
        SentimentResponse(score=3.0, emotion="HAPPINESS", aspect="PRODUCT")


def test_freeform_aspect():
    r = SentimentResponse(score=3.0, emotion="JOY", aspect="customer support")
    assert r.aspect == "customer support"


def test_irrelevant_response_omits_analysis_fields():
    r = SentimentResponse(is_relevant=False, irrelevance_reason="HOMONYM")
    assert r.is_relevant is False
    assert r.irrelevance_reason == IrrelevanceReason.HOMONYM


def test_irrelevant_response_accepts_all_reasons():
    for reason in ["HOMONYM", "SPAM", "EMPTY", "WRONG_LANGUAGE", "OTHER"]:
        r = SentimentResponse(is_relevant=False, irrelevance_reason=reason)
        assert r.irrelevance_reason == IrrelevanceReason(reason)


# --- AnalyzeResult ---


def test_analyze_result_relevant():
    r = AnalyzeResult(
        post_id=1,
        is_relevant=True,
        score=3.0,
        llm_score=3.0,
        emotion=Emotion.JOY,
        aspect="PRODUCT",
    )
    assert r.is_relevant is True
    assert r.score == 3.0


def test_analyze_result_irrelevant_nulls_analysis_fields():
    r = AnalyzeResult(
        post_id=1,
        is_relevant=False,
        irrelevance_reason=IrrelevanceReason.HOMONYM,
    )
    assert r.is_relevant is False
    assert r.score is None
    assert r.emotion is None
    assert r.aspect is None


# --- AnalyzerService ---


def _make_analyzer() -> tuple[AnalyzerService, AsyncMock]:
    settings = Settings(
        provider="ollama",
        base_url="http://localhost:9999/v1",
        api_key="test",
        model="test-model",
        rate_rpm=0,
        rate_tpm=0,
        max_concurrency=2,
    )
    llm = MagicMock()
    llm.analyze = AsyncMock()
    return AnalyzerService(llm, settings), llm.analyze


async def test_empty_after_cleaning_short_circuits_without_llm_call():
    analyzer, analyze = _make_analyzer()
    post = AnalyzeRequest(post_id=42, text="   https://example.com   @user  ")

    result = await analyzer.analyze_post(post)

    assert result.post_id == 42
    assert result.is_relevant is False
    assert result.irrelevance_reason == IrrelevanceReason.EMPTY
    assert result.score is None
    assert result.emotion is None
    analyze.assert_not_awaited()


async def test_llm_irrelevant_verdict_nulls_analysis_fields():
    analyzer, analyze = _make_analyzer()
    analyze.return_value = SentimentResponse(
        is_relevant=False,
        irrelevance_reason="HOMONYM",
        score=4.0,  # buggy model output — must not leak through
        emotion="JOY",
        aspect="PRODUCT",
    )

    result = await analyzer.analyze_post(
        AnalyzeRequest(post_id=7, text="some unrelated post about something else")
    )

    assert result.is_relevant is False
    assert result.irrelevance_reason == IrrelevanceReason.HOMONYM
    assert result.score is None
    assert result.llm_score is None
    assert result.emotion is None
    assert result.aspect is None


async def test_llm_irrelevant_without_reason_defaults_to_other():
    analyzer, analyze = _make_analyzer()
    analyze.return_value = SentimentResponse(is_relevant=False)

    result = await analyzer.analyze_post(
        AnalyzeRequest(post_id=8, text="borderline content")
    )

    assert result.is_relevant is False
    assert result.irrelevance_reason == IrrelevanceReason.OTHER


async def test_llm_relevant_verdict_populates_analysis_fields():
    analyzer, analyze = _make_analyzer()
    analyze.return_value = SentimentResponse(
        is_relevant=True, score=4.0, emotion="JOY", aspect="PRODUCT"
    )

    result = await analyzer.analyze_post(
        AnalyzeRequest(post_id=9, text="love this product")
    )

    assert result.is_relevant is True
    assert result.score == 4.0
    assert result.llm_score == 4.0
    assert result.emotion == Emotion.JOY
    assert result.aspect == "PRODUCT"
    assert result.irrelevance_reason is None


# --- session-affinity (prompt-cache routing) ---


async def test_analyze_batch_shares_one_session_id_across_posts():
    analyzer, analyze = _make_analyzer()
    analyze.return_value = SentimentResponse(
        is_relevant=True, score=3.0, emotion="JOY", aspect="PRODUCT"
    )

    await analyzer.analyze_batch(
        [
            AnalyzeRequest(post_id=1, text="first post"),
            AnalyzeRequest(post_id=2, text="second post"),
            AnalyzeRequest(post_id=3, text="third post"),
        ],
    )

    session_ids = {call.kwargs["session_id"] for call in analyze.await_args_list}
    assert len(session_ids) == 1
    assert next(iter(session_ids))  # non-empty


async def test_separate_batches_get_distinct_session_ids():
    analyzer, analyze = _make_analyzer()
    analyze.return_value = SentimentResponse(
        is_relevant=True, score=3.0, emotion="JOY", aspect="PRODUCT"
    )

    await analyzer.analyze_batch([AnalyzeRequest(post_id=1, text="a")])
    first_id = analyze.await_args_list[-1].kwargs["session_id"]

    await analyzer.analyze_batch([AnalyzeRequest(post_id=2, text="b")])
    second_id = analyze.await_args_list[-1].kwargs["session_id"]

    assert first_id != second_id
