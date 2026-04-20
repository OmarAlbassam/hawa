import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI

from config import Settings
from routes.analyze import router as analyze_router
from services.analyzer import AnalyzerService
from services.limits_probe import DiscoveredLimits, discover_limits
from services.llm_client import LLMClient
from services.rate_limiter import ProviderRateLimiter


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = Settings()
    logging.basicConfig(level=settings.log_level.upper())
    logger = logging.getLogger(__name__)

    logger.info(
        "LLM service starting — provider: %s, model: %s, endpoint: %s",
        settings.provider,
        settings.model,
        settings.base_url,
    )

    rate_limiter = ProviderRateLimiter(
        rpm=settings.rate_rpm,
        tpm=settings.rate_tpm,
    )
    llm_client = LLMClient(settings, rate_limiter)

    if settings.auto_discover_limits:
        discovered = await discover_limits(llm_client._raw_client, settings.model)
        rate_limiter = _apply_discovered(settings, rate_limiter, discovered, logger)
        llm_client.rate_limiter = rate_limiter

    app.state.settings = settings
    app.state.rate_limiter = rate_limiter
    app.state.analyzer = AnalyzerService(llm_client, settings)

    logger.info(
        "rate limits — rpm=%s, tpm=%s, max_concurrency=%s, max_retries=%s",
        rate_limiter.configured_rpm or "unlimited",
        rate_limiter.configured_tpm or "unlimited",
        settings.max_concurrency,
        settings.rate_max_retries,
    )

    yield


def _apply_discovered(
    settings: Settings,
    current: ProviderRateLimiter,
    discovered: DiscoveredLimits,
    logger: logging.Logger,
) -> ProviderRateLimiter:
    if discovered.empty:
        return current

    margin = settings.rate_safety_margin
    rpm = int(discovered.rpm * margin) if discovered.rpm else settings.rate_rpm
    tpm = int(discovered.tpm * margin) if discovered.tpm else settings.rate_tpm

    logger.info(
        "provider reports rpm=%s, tpm=%s → applying rpm=%s, tpm=%s (safety margin %.2f)",
        discovered.rpm if discovered.rpm is not None else "n/a",
        discovered.tpm if discovered.tpm is not None else "n/a",
        rpm,
        tpm,
        margin,
    )
    return ProviderRateLimiter(rpm=rpm, tpm=tpm)


app = FastAPI(title="Hawa LLM Service", version="0.1.0", lifespan=lifespan)
app.include_router(analyze_router)

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
