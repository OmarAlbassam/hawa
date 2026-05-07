"""Sentence embeddings for few-shot retrieval.

Two implementations satisfy the `Embedder` protocol:

- `SBERTEmbedder` — local `sentence-transformers`, default
  `BAAI/bge-small-en-v1.5` (384-dim, fast on CPU).
- `FireworksEmbedder` — Fireworks AI's OpenAI-compatible `/v1/embeddings`
  endpoint, default `nomic-ai/nomic-embed-text-v1.5` (768-dim).

Pick one via `make_embedder(provider, name)`.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Protocol

import numpy as np


class Embedder(Protocol):
    name: str
    dim: int

    def encode(self, texts: list[str]) -> np.ndarray: ...


SBERT_DEFAULT_MODEL = "BAAI/bge-small-en-v1.5"
FIREWORKS_DEFAULT_MODEL = "nomic-ai/nomic-embed-text-v1.5"
FIREWORKS_BASE_URL = "https://api.fireworks.ai/inference/v1"


@dataclass
class SBERTEmbedder:
    """Lazy-loaded sentence-transformers wrapper.

    The model loads on first `encode()` call so the CLI imports this module
    cheaply (e.g. for `benchmark --help`).
    """

    name: str = SBERT_DEFAULT_MODEL
    normalize: bool = True
    batch_size: int = 64

    def __post_init__(self) -> None:
        self._model = None
        self._dim: int | None = None

    @property
    def dim(self) -> int:
        if self._dim is None:
            self._ensure_loaded()
        assert self._dim is not None
        return self._dim

    def _ensure_loaded(self) -> None:
        if self._model is not None:
            return
        # Imported lazily so the CLI doesn't pay the import cost for every
        # invocation, only when an embedding is actually needed.
        from sentence_transformers import SentenceTransformer

        self._model = SentenceTransformer(self.name)
        self._dim = int(self._model.get_sentence_embedding_dimension())

    def encode(self, texts: list[str]) -> np.ndarray:
        self._ensure_loaded()
        assert self._model is not None
        embeddings = self._model.encode(
            texts,
            batch_size=self.batch_size,
            normalize_embeddings=self.normalize,
            show_progress_bar=False,
            convert_to_numpy=True,
        )
        return embeddings.astype(np.float32, copy=False)


def _resolve_fireworks_api_key() -> str:
    """Pick an API key for Fireworks embeddings.

    Priority:
      1. `FIREWORKS_API_KEY` — explicit, independent of LLM provider so an
         experiment can run LLM calls against (say) Groq while embeddings go
         through Fireworks.
      2. `LLM_API_KEY` — only when `LLM_PROVIDER=fireworks`, i.e. the same key
         is already authoritative for Fireworks elsewhere in the app.

    Pydantic-settings loads `.env` into the `Settings` object, not into
    `os.environ`, so an unrecognized var like `FIREWORKS_API_KEY` would be
    invisible to a plain `os.environ.get`. Load it explicitly here. With
    `override=False`, a shell-exported value still wins.
    """
    try:
        from dotenv import load_dotenv

        load_dotenv(override=False)
    except ImportError:
        pass
    key = os.environ.get("FIREWORKS_API_KEY")
    if key:
        return key
    if os.environ.get("LLM_PROVIDER", "").lower() == "fireworks":
        fallback = os.environ.get("LLM_API_KEY")
        if fallback:
            return fallback
    raise RuntimeError(
        "Fireworks embeddings need an API key. Set FIREWORKS_API_KEY in your "
        "environment (or LLM_API_KEY when LLM_PROVIDER=fireworks)."
    )


@dataclass
class FireworksEmbedder:
    """Fireworks AI embeddings via the OpenAI-compatible `/v1/embeddings`.

    Mirrors `LLMClient`'s pattern from `apps/llm/services/llm_client.py`:
    explicit httpx timeouts, `max_retries=0` so we don't stack SDK retries
    on top of the caller's policy. Vectors are L2-normalized so cosine
    similarity reduces to a dot product (matches `KNNStore`'s assumption).
    """

    name: str = FIREWORKS_DEFAULT_MODEL
    base_url: str = FIREWORKS_BASE_URL
    api_key: str | None = None  # resolved from env on first use if None
    batch_size: int = 64
    request_timeout_s: float = 60.0
    connect_timeout_s: float = 10.0
    normalize: bool = True
    # Internal — populated on first encode().
    _client: object = field(default=None, init=False, repr=False)
    _http_client: object = field(default=None, init=False, repr=False)
    _dim: int | None = field(default=None, init=False, repr=False)

    @property
    def dim(self) -> int:
        if self._dim is None:
            # We can't know the dim without a round-trip; force one with a
            # tiny payload. Caching means subsequent `dim` reads are free.
            self.encode(["dim probe"])
        assert self._dim is not None
        return self._dim

    def _ensure_client(self) -> None:
        if self._client is not None:
            return
        # Imported lazily so unrelated CLI invocations don't pay the import.
        import httpx
        from openai import OpenAI

        api_key = self.api_key or _resolve_fireworks_api_key()
        self._http_client = httpx.Client(
            timeout=httpx.Timeout(
                self.request_timeout_s,
                connect=self.connect_timeout_s,
            ),
        )
        self._client = OpenAI(
            base_url=self.base_url,
            api_key=api_key,
            max_retries=0,
            http_client=self._http_client,
        )

    def encode(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.zeros((0, self._dim or 0), dtype=np.float32)
        self._ensure_client()
        assert self._client is not None
        vectors: list[list[float]] = []
        for start in range(0, len(texts), self.batch_size):
            batch = texts[start : start + self.batch_size]
            response = self._client.embeddings.create(  # type: ignore[attr-defined]
                model=self.name,
                input=batch,
                encoding_format="float",
            )
            # The OpenAI SDK does not guarantee response order matches input
            # order — sort by `index` defensively.
            ordered = sorted(response.data, key=lambda d: d.index)
            vectors.extend(d.embedding for d in ordered)
        arr = np.asarray(vectors, dtype=np.float32)
        if self.normalize:
            norms = np.linalg.norm(arr, axis=1, keepdims=True)
            # Avoid divide-by-zero — a degenerate zero vector stays zero.
            norms[norms == 0] = 1.0
            arr = arr / norms
        if self._dim is None:
            self._dim = int(arr.shape[1])
        return arr


def make_embedder(provider: str, name: str | None = None) -> Embedder:
    """Construct an embedder by provider name.

    `name=None` resolves to the provider's default model (so the CLI's
    `--model` flag can be optional once `--provider` is given).
    """
    p = provider.lower()
    if p == "sbert":
        return SBERTEmbedder(name=name or SBERT_DEFAULT_MODEL)
    if p == "fireworks":
        return FireworksEmbedder(name=name or FIREWORKS_DEFAULT_MODEL)
    raise ValueError(
        f"unknown embedder provider: {provider!r} (expected 'sbert' or 'fireworks')"
    )
