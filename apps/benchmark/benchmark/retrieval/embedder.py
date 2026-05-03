"""Sentence embeddings for few-shot retrieval.

Wraps `sentence-transformers` with a small interface so the rest of the
benchmark depends on `Embedder`, not the SBERT API. Default model is
`BAAI/bge-small-en-v1.5` (384-dim, fast on CPU, strong English performance
for the size).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

import numpy as np


class Embedder(Protocol):
    name: str
    dim: int

    def encode(self, texts: list[str]) -> np.ndarray: ...


@dataclass
class SBERTEmbedder:
    """Lazy-loaded sentence-transformers wrapper.

    The model loads on first `encode()` call so the CLI imports this module
    cheaply (e.g. for `benchmark --help`).
    """

    name: str = "BAAI/bge-small-en-v1.5"
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
