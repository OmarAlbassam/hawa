import logging

import instructor
from openai import AsyncOpenAI

from config import Settings
from models import SentimentResponse

logger = logging.getLogger(__name__)


class LLMClient:
    def __init__(self, settings: Settings) -> None:
        self._raw_client = AsyncOpenAI(
            base_url=settings.base_url,
            api_key=settings.api_key,
        )
        self.client = instructor.from_openai(
            self._raw_client, mode=instructor.Mode.JSON
        )
        self.model = settings.model
        self.temperature = settings.temperature
        self.max_tokens = settings.max_tokens

    async def analyze(self, system_prompt: str, text: str) -> SentimentResponse:
        """Send text to LLM and return a validated SentimentResponse."""
        return await self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": text},
            ],
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            response_model=SentimentResponse,
            max_retries=2,
        )

    async def is_reachable(self) -> bool:
        """Check if the LLM endpoint is reachable."""
        try:
            await self._raw_client.models.list()
            return True
        except Exception:
            return False
