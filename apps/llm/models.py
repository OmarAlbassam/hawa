from enum import Enum

from pydantic import BaseModel, Field, field_validator


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


class IrrelevanceReason(str, Enum):
    OFF_TOPIC = "OFF_TOPIC"
    SPAM = "SPAM"
    EMPTY = "EMPTY"
    WRONG_LANGUAGE = "WRONG_LANGUAGE"
    OTHER = "OTHER"


# --- LLM Response ---


class SentimentResponse(BaseModel):
    """Schema for what the LLM returns. Used as instructor's response_model."""

    is_relevant: bool = True
    irrelevance_reason: IrrelevanceReason | None = None
    score: float = Field(default=2.5)
    emotion: Emotion = Emotion.NEUTRAL
    aspect: str = ""

    @field_validator("score", mode="before")
    @classmethod
    def clamp_score(cls, v: object) -> float:
        return max(0.0, min(5.0, float(v)))

    @field_validator("emotion", mode="before")
    @classmethod
    def uppercase_emotion(cls, v: object) -> str | object:
        return v.upper() if isinstance(v, str) else v


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
    aspect: str | None = None


class FailedResult(BaseModel):
    post_id: int
    error: str


class BatchAnalyzeResponse(BaseModel):
    results: list[AnalyzeResult]
    failed: list[FailedResult]


class HealthResponse(BaseModel):
    status: str
    llm_reachable: bool
    provider: str
    model: str
