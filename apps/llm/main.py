import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI

from config import Settings
from routes.analyze import router as analyze_router
from services.analyzer import AnalyzerService
from services.llm_client import LLMClient


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = Settings()
    logging.basicConfig(level=settings.log_level.upper())

    llm_client = LLMClient(settings)
    app.state.settings = settings
    app.state.analyzer = AnalyzerService(llm_client, settings)

    logger = logging.getLogger(__name__)
    logger.info(
        "LLM service starting — model: %s, endpoint: %s",
        settings.vllm_model,
        settings.vllm_base_url,
    )

    yield


app = FastAPI(title="Hawa LLM Service", version="0.1.0", lifespan=lifespan)
app.include_router(analyze_router)

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
