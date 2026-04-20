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


# --- LLM Response ---


class SentimentResponse(BaseModel):
    """Schema for what the LLM returns. Used as instructor's response_model."""

    score: float = Field(default=2.5)
    emotion: Emotion
    aspect: str  # TODO: define fixed aspect categories once requirements are settled

    @field_validator("score", mode="before")
    @classmethod
    def clamp_score(cls, v: object) -> float:
        return max(0.0, min(5.0, float(v)))

    @field_validator("emotion", mode="before")
    @classmethod
    def uppercase_emotion(cls, v: object) -> str:
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
    score: float = Field(ge=0.0, le=5.0)
    llm_score: float
    emotion: Emotion
    aspect: str  # TODO: define fixed aspect categories once requirements are settled


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
