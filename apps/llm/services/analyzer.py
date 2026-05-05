import asyncio
import logging

from openai import APIConnectionError

from config import Settings
from models import (
    AnalyzeRequest,
    AnalyzeResult,
    BatchAnalyzeResponse,
    FailedResult,
    IrrelevanceReason,
)
from prompts.sentiment import build_system_prompt
from services.llm_client import LLMClient, RateLimitExhaustedError
from utils.preprocessing import clean_text

logger = logging.getLogger(__name__)


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
        prompt: str | None = None,
    ) -> AnalyzeResult:
        """Preprocess, analyze, and validate a single post.

        Posts that are empty after cleaning short-circuit to an irrelevant
        result without calling the LLM. Posts that the LLM flags as
        is_relevant=False get their score/emotion/aspect nulled out so a
        buggy model output can't leak into aggregations.

        ``prompt`` is the system prompt to send. When omitted, it's built
        from the brand-context kwargs — the single-post path. ``analyze_batch``
        builds it once and passes it through so a 1000-post batch doesn't
        rebuild the same ~3KB string per post (and so vLLM's prefix cache sees
        a stable token sequence across the batch).
        """
        cleaned = clean_text(post.text, self.settings.max_text_length)
        if not cleaned:
            return AnalyzeResult(
                post_id=post.post_id,
                is_relevant=False,
                irrelevance_reason=IrrelevanceReason.EMPTY,
            )

        if prompt is None:
            prompt = build_system_prompt(brand_name, brand_industry, keywords)
        response = await self.llm_client.analyze(prompt, cleaned)

        if not response.is_relevant:
            return AnalyzeResult(
                post_id=post.post_id,
                is_relevant=False,
                irrelevance_reason=response.irrelevance_reason
                or IrrelevanceReason.OTHER,
            )

        return AnalyzeResult(
            post_id=post.post_id,
            is_relevant=True,
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
        """Analyze multiple posts concurrently with bounded parallelism.

        The rate limiter owned by the LLM client is the primary governor of
        outbound pace; this semaphore is a safety net on top of it.
        """
        results: list[AnalyzeResult] = []
        failed: list[FailedResult] = []
        semaphore = asyncio.Semaphore(self.settings.max_concurrency)
        prompt = build_system_prompt(brand_name, brand_industry, keywords)

        async def _run(post: AnalyzeRequest) -> None:
            async with semaphore:
                try:
                    result = await self.analyze_post(post, prompt=prompt)
                    results.append(result)
                except APIConnectionError:
                    failed.append(
                        FailedResult(post_id=post.post_id, error="LLM unreachable")
                    )
                except RateLimitExhaustedError as e:
                    logger.warning(
                        "rate limit exhausted for post %d after %d attempts",
                        post.post_id,
                        e.attempts,
                    )
                    failed.append(
                        FailedResult(
                            post_id=post.post_id,
                            error=f"rate_limited: {e}",
                        )
                    )
                except Exception as e:
                    logger.error("Failed to analyze post %d: %s", post.post_id, e)
                    failed.append(FailedResult(post_id=post.post_id, error=str(e)))

        await asyncio.gather(*[_run(post) for post in posts])
        return BatchAnalyzeResponse(results=results, failed=failed)
