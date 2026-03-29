import asyncio
import json
import logging

from openai import APIConnectionError
from pydantic import ValidationError

from config import Settings
from models import (
    AnalyzeRequest,
    AnalyzeResult,
    BatchAnalyzeResponse,
    FailedResult,
)
from prompts.sentiment import build_system_prompt
from services.llm_client import LLMClient
from utils.preprocessing import clean_text

logger = logging.getLogger(__name__)

MAX_CONCURRENCY = 5


def _clamp(value: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, value))


def _normalize_result(raw: dict, post_id: int) -> AnalyzeResult:
    """Validate and normalize LLM output into an AnalyzeResult."""
    raw["score"] = _clamp(float(raw.get("score", 2.5)), 0.0, 5.0)
    if isinstance(raw.get("emotion"), str):
        raw["emotion"] = raw["emotion"].upper()
    if isinstance(raw.get("aspect"), str):
        raw["aspect"] = raw["aspect"].upper()

    return AnalyzeResult(
        post_id=post_id,
        score=raw["score"],
        llm_score=raw["score"],
        emotion=raw["emotion"],
        aspect=raw["aspect"],

    )


class AnalyzerService:
    def __init__(self, llm_client: LLMClient, settings: Settings) -> None:
        self.llm_client = llm_client
        self.settings = settings

    async def analyze_post(
        self,
        post: AnalyzeRequest,
        *,
        brand_name: str | None = None,
        brand_industry: str | None = None,
        keywords: list[str] | None = None,
    ) -> AnalyzeResult:
        """Preprocess, analyze, and validate a single post."""
        cleaned = clean_text(post.text, self.settings.max_text_length)
        prompt = build_system_prompt(brand_name, brand_industry, keywords)

        last_error: Exception | None = None
        for attempt in range(2):
            try:
                raw = await self.llm_client.analyze(prompt, cleaned)
                return _normalize_result(raw, post.post_id)
            except (json.JSONDecodeError, ValidationError, KeyError, ValueError) as e:
                last_error = e
                if attempt == 0:
                    logger.warning(
                        "Retrying post %d after parse error: %s", post.post_id, e
                    )
                    continue
                raise

        raise last_error  # type: ignore[misc]

    async def analyze_batch(
        self,
        posts: list[AnalyzeRequest],
        *,
        brand_name: str | None = None,
        brand_industry: str | None = None,
        keywords: list[str] | None = None,
    ) -> BatchAnalyzeResponse:
        """Analyze multiple posts concurrently with bounded parallelism."""
        results: list[AnalyzeResult] = []
        failed: list[FailedResult] = []
        semaphore = asyncio.Semaphore(MAX_CONCURRENCY)

        async def _run(post: AnalyzeRequest) -> None:
            async with semaphore:
                try:
                    result = await self.analyze_post(
                        post,
                        brand_name=brand_name,
                        brand_industry=brand_industry,
                        keywords=keywords,
                    )
                    results.append(result)
                except APIConnectionError:
                    failed.append(
                        FailedResult(post_id=post.post_id, error="LLM unreachable")
                    )
                except Exception as e:
                    logger.error("Failed to analyze post %d: %s", post.post_id, e)
                    failed.append(FailedResult(post_id=post.post_id, error=str(e)))

        await asyncio.gather(*[_run(post) for post in posts])
        return BatchAnalyzeResponse(results=results, failed=failed)
