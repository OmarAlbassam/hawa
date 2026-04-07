import asyncio
import logging

from openai import APIConnectionError

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
        response = await self.llm_client.analyze(prompt, cleaned)
        return AnalyzeResult(
            post_id=post.post_id,
            score=response.score,
            llm_score=response.score,
            emotion=response.emotion,
            aspect=response.aspect,
        )

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
