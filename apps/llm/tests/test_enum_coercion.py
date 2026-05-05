"""Tests for the snap-to-enum coercion layer.

These run without sentence-transformers — embedding-backend tests stub the
SBERT call so CI doesn't pull a model.
"""

from __future__ import annotations

from unittest.mock import patch

import pytest

from models import Aspect, Emotion
from services import enum_coercion
from services.enum_coercion import (
    ASPECT_SYNONYMS,
    EMOTION_SYNONYMS,
    coerce_emotion,
    snap_to_enum,
)


# --- direct enum + exact-match path -----------------------------------------


def test_enum_member_passes_through():
    assert (
        snap_to_enum(
            Emotion.JOY,
            Emotion,
            synonyms=EMOTION_SYNONYMS,
            fallback=Emotion.NEUTRAL,
        )
        is Emotion.JOY
    )


def test_lowercase_value_normalises():
    assert (
        snap_to_enum(
            "joy",
            Emotion,
            synonyms=EMOTION_SYNONYMS,
            fallback=Emotion.NEUTRAL,
        )
        is Emotion.JOY
    )


def test_padded_value_normalises():
    assert (
        snap_to_enum(
            "  ANGER  ",
            Emotion,
            synonyms=EMOTION_SYNONYMS,
            fallback=Emotion.NEUTRAL,
        )
        is Emotion.ANGER
    )


def test_empty_returns_fallback():
    assert (
        snap_to_enum(
            "",
            Aspect,
            synonyms=ASPECT_SYNONYMS,
            fallback=Aspect.OTHER,
        )
        is Aspect.OTHER
    )


def test_none_returns_fallback():
    assert (
        snap_to_enum(
            None,
            Aspect,
            synonyms=ASPECT_SYNONYMS,
            fallback=Aspect.OTHER,
        )
        is Aspect.OTHER
    )


# --- synonym path -----------------------------------------------------------


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("MAD", Emotion.ANGER),
        ("happy", Emotion.JOY),
        ("ecstatic", Emotion.JOY),
        ("disappointed", Emotion.SADNESS),
        ("anxious", Emotion.FEAR),
    ],
)
def test_emotion_synonym_lookup(raw: str, expected: Emotion):
    assert (
        snap_to_enum(
            raw, Emotion, synonyms=EMOTION_SYNONYMS, fallback=Emotion.NEUTRAL
        )
        is expected
    )


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("products", Aspect.PRODUCT),
        ("customer support", Aspect.SERVICE),
        ("shipping", Aspect.DELIVERY),
        ("value for money", Aspect.PRICING),
        ("branding", Aspect.BRAND),
    ],
)
def test_aspect_synonym_lookup(raw: str, expected: Aspect):
    assert (
        snap_to_enum(
            raw, Aspect, synonyms=ASPECT_SYNONYMS, fallback=Aspect.OTHER
        )
        is expected
    )


# --- fallback ---------------------------------------------------------------


def test_unknown_emotion_falls_back_with_synonyms_backend():
    assert (
        snap_to_enum(
            "zorgblat",
            Emotion,
            synonyms=EMOTION_SYNONYMS,
            fallback=Emotion.NEUTRAL,
            backend="synonyms",
        )
        is Emotion.NEUTRAL
    )


def test_unknown_aspect_falls_back_with_synonyms_backend():
    assert (
        snap_to_enum(
            "kerflump",
            Aspect,
            synonyms=ASPECT_SYNONYMS,
            fallback=Aspect.OTHER,
            backend="synonyms",
        )
        is Aspect.OTHER
    )


def test_off_backend_raises_on_unknown():
    """`backend='off'` reverts to strict pydantic-style rejection."""
    with pytest.raises(ValueError):
        snap_to_enum(
            "MAD",  # would map via synonyms, but backend='off' skips them
            Emotion,
            synonyms=EMOTION_SYNONYMS,
            fallback=Emotion.NEUTRAL,
            backend="off",
        )


# --- embedding backend (mocked) ---------------------------------------------


def test_embedding_backend_returns_nearest_when_above_threshold():
    """Mock `_embedding_nearest` to verify the backend path is wired up."""
    with patch(
        "services.enum_coercion._embedding_nearest", return_value=Emotion.ANGER
    ) as mock:
        result = snap_to_enum(
            "extremely cross",  # not in synonyms
            Emotion,
            synonyms={},
            fallback=Emotion.NEUTRAL,
            backend="embedding",
        )
    assert result is Emotion.ANGER
    mock.assert_called_once()


def test_embedding_backend_falls_back_when_nearest_below_threshold():
    with patch(
        "services.enum_coercion._embedding_nearest", return_value=None
    ):
        result = snap_to_enum(
            "qwjklasd",
            Emotion,
            synonyms={},
            fallback=Emotion.NEUTRAL,
            backend="embedding",
        )
    assert result is Emotion.NEUTRAL


# --- configure()-driven backend selection ----------------------------------


@pytest.fixture
def restore_enum_coercion_config():
    """Snapshot and restore the module-level config so tests don't bleed."""
    saved = (enum_coercion._backend, enum_coercion._threshold)
    yield
    enum_coercion._backend, enum_coercion._threshold = saved


def test_configure_off_makes_validators_strict(restore_enum_coercion_config):
    """`configure(backend='off')` must take effect even though `.env` /
    pydantic-settings doesn't export to os.environ. Regression: the previous
    implementation read os.environ at import time and ignored Settings."""
    enum_coercion.configure("off", 0.45)
    with pytest.raises(ValueError):
        coerce_emotion("zorgblat")


def test_configure_synonyms_coerces(restore_enum_coercion_config):
    enum_coercion.configure("synonyms", 0.45)
    assert coerce_emotion("MAD") is Emotion.ANGER


def test_configure_unknown_backend_falls_back_to_synonyms(
    restore_enum_coercion_config,
):
    enum_coercion.configure("bogus", 0.45)
    assert enum_coercion._backend == "synonyms"


def test_embedding_backend_handles_loader_failure_gracefully():
    """If sentence-transformers can't load, fall back rather than crash."""
    with patch(
        "services.enum_coercion._embedding_nearest",
        side_effect=RuntimeError("torch unavailable"),
    ):
        result = snap_to_enum(
            "qwjklasd",
            Emotion,
            synonyms={},
            fallback=Emotion.NEUTRAL,
            backend="embedding",
        )
    assert result is Emotion.NEUTRAL
