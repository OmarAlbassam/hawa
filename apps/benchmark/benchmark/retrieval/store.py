"""In-memory kNN store with fold-based leakage prevention.

For ~100s of labeled samples there is no reason to use a real vector DB.
A normalized (N, D) numpy array plus one matmul gives top-K cosine in
microseconds, and the whole index serializes to a single .npz file.

The critical property is **leakage prevention**: when we retrieve few-shot
exemplars for a test post, we must mask out (a) the post itself and (b) any
sample that shares its fold (which is also in the held-out set on this
iteration). Without this, retrieved few-shot scores are meaningless.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np

from benchmark.dataset import Sample
from benchmark.retrieval.embedder import Embedder


@dataclass
class RetrievedExample:
    sample: Sample
    similarity: float


class KNNStore:
    """Cosine-similarity kNN over normalized embeddings.

    Embeddings are assumed L2-normalized (the default for `SBERTEmbedder`),
    so cosine similarity reduces to a dot product.
    """

    def __init__(
        self,
        samples: list[Sample],
        embeddings: np.ndarray,
        embedder_name: str,
    ) -> None:
        if len(samples) != embeddings.shape[0]:
            raise ValueError(
                f"sample/embedding length mismatch: {len(samples)} vs {embeddings.shape[0]}"
            )
        if embeddings.ndim != 2:
            raise ValueError(f"embeddings must be 2D, got shape {embeddings.shape}")
        self.samples = samples
        self.embeddings = embeddings.astype(np.float32, copy=False)
        self.embedder_name = embedder_name
        self._post_id_to_index = {s.post_id: i for i, s in enumerate(samples)}

    @classmethod
    def build(cls, samples: list[Sample], embedder: Embedder) -> "KNNStore":
        texts = [s.text for s in samples]
        embeddings = embedder.encode(texts)
        return cls(samples, embeddings, embedder_name=embedder.name)

    def save(self, path: str | Path) -> None:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        np.savez(
            path,
            embeddings=self.embeddings,
            post_ids=np.array([s.post_id for s in self.samples], dtype=np.int64),
            embedder_name=np.array(self.embedder_name),
        )

    @classmethod
    def load(
        cls,
        path: str | Path,
        samples: list[Sample],
        *,
        expected_embedder_name: str | None = None,
    ) -> "KNNStore":
        path = Path(path)
        data = np.load(path, allow_pickle=False)
        embeddings = data["embeddings"]
        post_ids = data["post_ids"].tolist()
        embedder_name = str(data["embedder_name"])

        # The npz stores the embedder identity. With multiple providers in
        # play (SBERT, Fireworks) a mismatched npz silently produces wrong
        # nearest neighbors — fail loudly when the runtime embedder differs.
        if expected_embedder_name is not None and embedder_name != expected_embedder_name:
            raise ValueError(
                f"saved index was built with embedder {embedder_name!r}, "
                f"runtime is using {expected_embedder_name!r} — rerun "
                f"`benchmark embed --model {expected_embedder_name}` "
                f"(and pass `--provider` if the new embedder is Fireworks)"
            )

        # Re-align samples to the order in the saved index. If a sample is
        # missing from the saved file (dataset grew), fail loudly — the user
        # should re-embed.
        by_id = {s.post_id: s for s in samples}
        missing = [pid for pid in post_ids if pid not in by_id]
        extra = [s.post_id for s in samples if s.post_id not in set(post_ids)]
        if missing or extra:
            raise ValueError(
                f"saved index out of sync with dataset: "
                f"missing={missing[:5]} extra={extra[:5]} — rerun `benchmark embed`"
            )
        ordered = [by_id[pid] for pid in post_ids]
        return cls(ordered, embeddings, embedder_name=embedder_name)

    def query(
        self,
        query_text: str,
        embedder: Embedder,
        k: int,
        *,
        exclude_post_ids: set[int] | None = None,
        exclude_folds: set[int] | None = None,
    ) -> list[RetrievedExample]:
        """Return top-k samples by cosine similarity to `query_text`.

        Use `exclude_post_ids` to prevent a post from retrieving itself.
        Use `exclude_folds` to enforce k-fold leakage prevention — pass the
        fold of the test post (and any other folds in the held-out set).
        """
        if k <= 0:
            return []
        q = embedder.encode([query_text])[0]
        sims = self.embeddings @ q  # (N,) since q is normalized

        mask = self._build_mask(exclude_post_ids, exclude_folds)
        if mask is not None:
            sims = sims.copy()
            sims[mask] = -np.inf

        # argpartition gives the top-k indices in unsorted order, then sort
        # only those k. O(N + k log k) vs O(N log N) for a full sort.
        usable = int(np.sum(np.isfinite(sims))) if mask is not None else len(sims)
        k = min(k, usable)
        if k <= 0:
            return []
        top_unsorted = np.argpartition(-sims, k - 1)[:k]
        top_sorted = top_unsorted[np.argsort(-sims[top_unsorted])]
        return [
            RetrievedExample(sample=self.samples[i], similarity=float(sims[i]))
            for i in top_sorted
        ]

    def _build_mask(
        self,
        exclude_post_ids: set[int] | None,
        exclude_folds: set[int] | None,
    ) -> np.ndarray | None:
        if not exclude_post_ids and not exclude_folds:
            return None
        mask = np.zeros(len(self.samples), dtype=bool)
        if exclude_post_ids:
            for pid in exclude_post_ids:
                idx = self._post_id_to_index.get(pid)
                if idx is not None:
                    mask[idx] = True
        if exclude_folds:
            for i, s in enumerate(self.samples):
                if s.fold in exclude_folds:
                    mask[i] = True
        return mask
