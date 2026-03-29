from enum import Enum

from pydantic import BaseModel, Field


class Emotion(str, Enum):
    JOY = "JOY"
    ANGER = "ANGER"
    SADNESS = "SADNESS"
    FEAR = "FEAR"
    SURPRISE = "SURPRISE"
    DISGUST = "DISGUST"


class Aspect(str, Enum):
    PRODUCT = "PRODUCT"
    SERVICE = "SERVICE"
    DELIVERY = "DELIVERY"
    PRICING = "PRICING"


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
    aspect: Aspect


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
