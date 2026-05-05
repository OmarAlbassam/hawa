"""Snap free-form LLM strings to a fixed enum.

Provider-side constrained decoding (vLLM ``guided_json``, Groq strict
``json_schema``, Ollama ``format``) makes out-of-enum outputs structurally
impossible — but only on backends that support it. For Groq Llama-3.x and
Qwen3 we still get raw JSON-mode, where the model can emit "MAD" for emotion
or "store" for aspect. This module is the cross-provider safety net: any
string in, a guaranteed enum member out.

Strategy, cheapest-to-most-expensive:

1. Already an enum member → return as-is.
2. Empty / None → fallback (used for ``is_relevant=False`` payloads).
3. Case-normalized exact match against member names.
4. Curated synonym table (``"mad" → ANGER``, ``"products" → PRODUCT``).
5. Optional embedding nearest-neighbour for novel words. Off by default to
   keep production deps light; flip on with ``LLM_ENUM_COERCION_BACKEND=embedding``.
6. Sentinel fallback. Logged at WARNING so the synonym table can grow.
"""

from __future__ import annotations

import logging
import os
import re
import threading
from collections.abc import Mapping
from enum import Enum
from typing import TYPE_CHECKING, TypeVar

if TYPE_CHECKING:
    import numpy as np

logger = logging.getLogger(__name__)

E = TypeVar("E", bound=Enum)

_NORMALIZE_RE = re.compile(r"[^a-z]+")


# Hand-curated synonyms. Lowercase, alphanumeric-only keys (after _norm_key);
# values are the canonical enum NAME strings. Grow these from coercion logs.
EMOTION_SYNONYMS: dict[str, str] = {
    # JOY family
    "happy": "JOY",
    "happiness": "JOY",
    "joyful": "JOY",
    "delighted": "JOY",
    "thrilled": "JOY",
    "ecstatic": "JOY",
    "love": "JOY",
    "loving": "JOY",
    "excited": "JOY",
    "excitement": "JOY",
    "pleased": "JOY",
    "satisfied": "JOY",
    "satisfaction": "JOY",
    # ANGER family
    "mad": "ANGER",
    "angry": "ANGER",
    "furious": "ANGER",
    "rage": "ANGER",
    "annoyed": "ANGER",
    "annoyance": "ANGER",
    "frustrated": "ANGER",
    "frustration": "ANGER",
    "irritated": "ANGER",
    "irritation": "ANGER",
    # SADNESS family
    "sad": "SADNESS",
    "unhappy": "SADNESS",
    "disappointed": "SADNESS",
    "disappointment": "SADNESS",
    "depressed": "SADNESS",
    "upset": "SADNESS",
    "grief": "SADNESS",
    # FEAR family
    "afraid": "FEAR",
    "scared": "FEAR",
    "worried": "FEAR",
    "anxious": "FEAR",
    "anxiety": "FEAR",
    "concerned": "FEAR",
    "nervous": "FEAR",
    # SURPRISE family
    "surprised": "SURPRISE",
    "shocked": "SURPRISE",
    "shock": "SURPRISE",
    "astonished": "SURPRISE",
    "amazed": "SURPRISE",
    "amazement": "SURPRISE",
    # DISGUST family
    "disgusted": "DISGUST",
    "revolted": "DISGUST",
    "repulsed": "DISGUST",
    # NEUTRAL family
    "none": "NEUTRAL",
    "neutralemotion": "NEUTRAL",
    "noemotion": "NEUTRAL",
    "indifferent": "NEUTRAL",
    "calm": "NEUTRAL",
}

ASPECT_SYNONYMS: dict[str, str] = {
    # PRODUCT
    "product": "PRODUCT",
    "products": "PRODUCT",
    "productquality": "PRODUCT",
    "qualityofproduct": "PRODUCT",
    "device": "PRODUCT",
    "hardware": "PRODUCT",
    "software": "PRODUCT",
    "feature": "PRODUCT",
    "features": "PRODUCT",
    # SERVICE
    "service": "SERVICE",
    "services": "SERVICE",
    "customerservice": "SERVICE",
    "customersupport": "SERVICE",
    "support": "SERVICE",
    "staff": "SERVICE",
    "employees": "SERVICE",
    # DELIVERY
    "delivery": "DELIVERY",
    "shipping": "DELIVERY",
    "shippingspeed": "DELIVERY",
    "logistics": "DELIVERY",
    "fulfillment": "DELIVERY",
    "shipment": "DELIVERY",
    # PRICING
    "price": "PRICING",
    "pricing": "PRICING",
    "cost": "PRICING",
    "value": "PRICING",
    "valueformoney": "PRICING",
    "expensive": "PRICING",
    "cheap": "PRICING",
    "affordable": "PRICING",
    # BRAND
    "brand": "BRAND",
    "branding": "BRAND",
    "reputation": "BRAND",
    "image": "BRAND",
    "company": "BRAND",
}


def _norm_key(s: str) -> str:
    """Reduce free-form text to the lookup key used in synonym maps."""
    return _NORMALIZE_RE.sub("", s.lower())


# Embedding backend state. Lazy-loaded so plain `synonyms` mode stays free of
# the sentence-transformers import.
_embedding_lock = threading.Lock()
_embedder: object | None = None
_enum_embeddings: dict[type[Enum], tuple[tuple[Enum, ...], "np.ndarray"]] = {}


def _get_embedder():
    global _embedder
    if _embedder is not None:
        return _embedder
    with _embedding_lock:
        if _embedder is None:
            from sentence_transformers import SentenceTransformer

            _embedder = SentenceTransformer("BAAI/bge-small-en-v1.5")
    return _embedder


def _enum_matrix(enum_cls: type[Enum]):
    """Embed each enum member's name once and cache the matrix."""
    cached = _enum_embeddings.get(enum_cls)
    if cached is not None:
        return cached
    import numpy as np

    embedder = _get_embedder()
    members = tuple(enum_cls)
    texts = [m.name for m in members]
    matrix = embedder.encode(
        texts, normalize_embeddings=True, show_progress_bar=False
    ).astype(np.float32, copy=False)
    _enum_embeddings[enum_cls] = (members, matrix)
    return members, matrix


def _embedding_nearest(
    raw: str, enum_cls: type[Enum], threshold: float
) -> Enum | None:
    """Return the closest enum member by cosine, or None if below threshold."""
    import numpy as np

    embedder = _get_embedder()
    members, matrix = _enum_matrix(enum_cls)
    q = embedder.encode(
        [raw], normalize_embeddings=True, show_progress_bar=False
    ).astype(np.float32, copy=False)[0]
    sims = matrix @ q
    best = int(np.argmax(sims))
    if float(sims[best]) < threshold:
        return None
    return members[best]


def snap_to_enum(
    value: object,
    enum_cls: type[E],
    *,
    synonyms: Mapping[str, str],
    fallback: E,
    backend: str = "synonyms",
    embedding_threshold: float = 0.45,
) -> E:
    """Coerce `value` to a member of `enum_cls`, with `fallback` as last resort.

    `backend` is one of "off" (no coercion past enum/exact match — caller is
    responsible for handling rejects), "synonyms" (default), or "embedding"
    (synonyms + SBERT nearest-neighbour). Coercion past the synonym table
    emits a WARNING log so the table can be grown from real traffic.
    """
    if isinstance(value, enum_cls):
        return value
    if value is None:
        return fallback

    s = str(value).strip()
    if not s:
        return fallback

    upper = s.upper()
    try:
        return enum_cls[upper]
    except KeyError:
        pass
    try:
        return enum_cls(upper)  # value-based lookup; same as name for StrEnums here
    except ValueError:
        pass

    if backend == "off":
        # Caller asked for strict — let the model-level enum validator reject it.
        return enum_cls(upper)  # raises ValueError; pydantic surfaces it

    key = _norm_key(s)
    canonical = synonyms.get(key)
    if canonical is not None:
        try:
            return enum_cls[canonical]
        except KeyError:
            logger.error(
                "synonym table maps %r → %r which is not a member of %s",
                s, canonical, enum_cls.__name__,
            )

    if backend == "embedding":
        try:
            nearest = _embedding_nearest(s, enum_cls, embedding_threshold)
        except Exception as e:  # pragma: no cover — guard against torch import / OOM
            logger.warning(
                "embedding fallback failed for %s=%r (%s); using sentinel",
                enum_cls.__name__, s, e,
            )
            nearest = None
        if nearest is not None:
            logger.info(
                "coercion_embedding enum=%s raw=%r → %s",
                enum_cls.__name__, s, nearest.name,
            )
            return nearest

    logger.warning(
        "coercion_fallback enum=%s raw=%r → %s "
        "(consider adding a synonym entry)",
        enum_cls.__name__, s, fallback.name,
    )
    return fallback


# Backend config. Settings can't be imported here (would create a cycle:
# models → enum_coercion → config → ...), so the FastAPI lifespan and the
# benchmark runner push values in via `configure()` after constructing
# Settings. Until then — and for ad-hoc imports / tests — we resolve from
# os.environ on first use. Reading at import time was wrong: pydantic-settings
# loads `.env` into Settings without exporting back to os.environ, so any
# `.env`-only override was silently ignored.
_VALID_BACKENDS = {"off", "synonyms", "embedding"}
_backend: str | None = None
_threshold: float | None = None


def configure(backend: str, threshold: float) -> None:
    """Set the active enum-coercion backend and embedding threshold.

    Called from the LLM service lifespan and from the benchmark runner with
    values resolved by pydantic-settings (i.e. honoring `.env`).
    """
    global _backend, _threshold
    normalized = backend.strip().lower()
    if normalized not in _VALID_BACKENDS:
        logger.warning(
            "unknown enum_coercion_backend=%r; falling back to 'synonyms'",
            backend,
        )
        normalized = "synonyms"
    _backend = normalized
    _threshold = float(threshold)


def _resolve_config() -> tuple[str, float]:
    """Return (backend, threshold), resolving from env on first call if unset."""
    global _backend, _threshold
    if _backend is None or _threshold is None:
        env_backend = os.environ.get("LLM_ENUM_COERCION_BACKEND", "synonyms").strip().lower()
        if env_backend not in _VALID_BACKENDS:
            logger.warning(
                "unknown LLM_ENUM_COERCION_BACKEND=%r; falling back to 'synonyms'",
                env_backend,
            )
            env_backend = "synonyms"
        _backend = env_backend
        _threshold = float(
            os.environ.get("LLM_ENUM_COERCION_EMBEDDING_THRESHOLD", "0.45")
        )
    return _backend, _threshold


def coerce_emotion(value: object) -> object:
    """Pydantic ``mode='before'`` validator helper for the Emotion field.

    Returns an ``Emotion`` member (Pydantic accepts and passes through). On a
    ``backend='off'`` miss this raises ValueError, which Pydantic re-raises
    as ValidationError — matching the pre-coercion strict behavior.
    """
    # Imported here, not at module top, to avoid the circular import
    # models → enum_coercion → models.
    from models import Emotion

    backend, threshold = _resolve_config()
    return snap_to_enum(
        value,
        Emotion,
        synonyms=EMOTION_SYNONYMS,
        fallback=Emotion.NEUTRAL,
        backend=backend,
        embedding_threshold=threshold,
    )


def coerce_aspect(value: object) -> object:
    """Pydantic ``mode='before'`` validator helper for the Aspect field."""
    from models import Aspect

    backend, threshold = _resolve_config()
    return snap_to_enum(
        value,
        Aspect,
        synonyms=ASPECT_SYNONYMS,
        fallback=Aspect.OTHER,
        backend=backend,
        embedding_threshold=threshold,
    )
