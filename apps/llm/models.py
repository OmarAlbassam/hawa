from decimal import ROUND_HALF_UP, Decimal
from enum import Enum

from pydantic import BaseModel, Field, field_validator


def _snap_to_half_step(value: float) -> float:
    """Clamp to [0, 5] and snap to the nearest 0.5 using HALF_UP."""
    clamped = max(0.0, min(5.0, value))
    snapped = (Decimal(str(clamped)) * 2).quantize(Decimal("1"), rounding=ROUND_HALF_UP) / 2
    return float(snapped)


class Emotion(str, Enum):
    JOY = "JOY"
    ANGER = "ANGER"
    SADNESS = "SADNESS"
    FEAR = "FEAR"
    SURPRISE = "SURPRISE"
    DISGUST = "DISGUST"
    NEUTRAL = "NEUTRAL"


class Aspect(str, Enum):
    PRODUCT = "PRODUCT"
    SERVICE = "SERVICE"
    DELIVERY = "DELIVERY"
    PRICING = "PRICING"
    BRAND = "BRAND"
    # Sentinel for "relevant post, but doesn't fit any of the named aspects."
    # Also the coercion fallback when the LLM produces a string that can't be
    # mapped via the synonym table or embedding nearest-neighbour.
    OTHER = "OTHER"


class IrrelevanceReason(str, Enum):
    HOMONYM = "HOMONYM"
    SPAM = "SPAM"
    EMPTY = "EMPTY"
    WRONG_LANGUAGE = "WRONG_LANGUAGE"
    OTHER = "OTHER"


# --- LLM Response ---


class SentimentResponse(BaseModel):
    """Schema for what the LLM returns; parsed from JSON-mode output.

    `emotion` and `aspect` are coerced through `services.enum_coercion`, so
    any free-form LLM string ("MAD", "store", "customer support") snaps to a
    valid enum member rather than raising. This is the cross-provider safety
    net for backends that can't do constrained decoding (Groq Llama-3.x,
    Qwen3 on Groq). On backends that can (vLLM `guided_json`, Groq strict
    `json_schema`), the LLM cannot emit a non-enum value and the coercion
    layer is a no-op.
    """

    is_relevant: bool = True
    irrelevance_reason: IrrelevanceReason | None = None
    score: float = Field(default=2.5)
    emotion: Emotion = Emotion.NEUTRAL
    aspect: Aspect = Aspect.OTHER

    @field_validator("score", mode="before")
    @classmethod
    def clamp_and_snap_score(cls, v: object) -> float:
        return _snap_to_half_step(float(v))

    @field_validator("emotion", mode="before")
    @classmethod
    def coerce_emotion(cls, v: object) -> object:
        from services.enum_coercion import coerce_emotion

        return coerce_emotion(v)

    @field_validator("aspect", mode="before")
    @classmethod
    def coerce_aspect(cls, v: object) -> object:
        from services.enum_coercion import coerce_aspect

        return coerce_aspect(v)


# --- Requests ---


class AnalyzeRequest(BaseModel):
    post_id: int
    text: str


class BatchAnalyzeRequest(BaseModel):
    posts: list[AnalyzeRequest]
    brand_name: str | None = None
    brand_industry: str | None = None
    keywords: list[str] | None = None


# --- Responses ---


class AnalyzeResult(BaseModel):
    post_id: int
    is_relevant: bool = True
    irrelevance_reason: IrrelevanceReason | None = None
    score: float | None = Field(default=None, ge=0.0, le=5.0)
    llm_score: float | None = None
    emotion: Emotion | None = None
    aspect: Aspect | None = None


class FailedResult(BaseModel):
    post_id: int
    error: str


class BatchAnalyzeResponse(BaseModel):
    results: list[AnalyzeResult]
    failed: list[FailedResult]


class HealthResponse(BaseModel):
    status: str
    # None when the caller didn't request a deep check (default). The shallow
    # check intentionally doesn't touch the LLM — on RunPod that would
    # cold-start a worker on every probe.
    llm_reachable: bool | None
    provider: str
    model: str
