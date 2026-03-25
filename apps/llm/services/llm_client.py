import json
import logging

from openai import AsyncOpenAI

from config import Settings

logger = logging.getLogger(__name__)


class LLMClient:
    def __init__(self, settings: Settings) -> None:
        self.client = AsyncOpenAI(
            base_url=settings.vllm_base_url,
            api_key=settings.vllm_api_key,
        )
        self.model = settings.vllm_model
        self.temperature = settings.temperature
        self.max_tokens = settings.max_tokens

    async def analyze(self, system_prompt: str, text: str) -> dict:
        """Send text to vLLM with the given system prompt. Returns parsed JSON dict."""
        response = await self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": text},
            ],
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            response_format={"type": "json_object"},
        )
        raw = response.choices[0].message.content
        logger.debug("LLM raw response: %s", raw)
        return json.loads(raw)

    async def is_reachable(self) -> bool:
        """Check if the vLLM endpoint is reachable."""
        try:
            await self.client.models.list()
            return True
        except Exception:
            return False
