from enum import StrEnum

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

PROVIDER_DEFAULTS: dict[str, dict[str, str]] = {
    "ollama": {
        "base_url": "http://localhost:11434/v1",
        "api_key": "ollama",
        "model": "llama3.1:8b", # 
    },
    "runpod": {
        "model": "meta-llama/Llama-3.1-8B-Instruct",
    },
}


class Provider(StrEnum):
    OLLAMA = "ollama"
    RUNPOD = "runpod"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="LLM_")

    # Provider
    provider: Provider = Provider.OLLAMA

    # LLM connection
    base_url: str = ""
    api_key: str = ""
    model: str = ""

    # Service
    host: str = "0.0.0.0"
    port: int = 8001
    log_level: str = "info"

    # LLM parameters
    temperature: float = 0.1
    max_tokens: int = 512

    # Preprocessing
    max_text_length: int = 2048

    @model_validator(mode="before")
    @classmethod
    def apply_provider_defaults(cls, values: dict) -> dict:
        provider = values.get("provider", values.get("LLM_PROVIDER", "ollama")).lower()
        defaults = PROVIDER_DEFAULTS.get(provider, {})
        for field, default in defaults.items():
            if not values.get(field):
                values[field] = default
        return values
