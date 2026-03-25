from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="LLM_")

    # vLLM connection
    vllm_base_url: str = "http://localhost:8000/v1"
    vllm_api_key: str = "EMPTY"
    vllm_model: str = "meta-llama/Llama-3.1-8B-Instruct"

    # Service
    host: str = "0.0.0.0"
    port: int = 8001
    log_level: str = "info"

    # LLM parameters
    temperature: float = 0.1
    max_tokens: int = 512

    # Preprocessing
    max_text_length: int = 2048
