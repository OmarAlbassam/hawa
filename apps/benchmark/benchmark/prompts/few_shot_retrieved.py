"""Retrieved few-shot — pick the K most similar labeled examples per post.

Uses the embedding-backed `KNNStore` to find the K nearest training
exemplars (by cosine similarity) and injects them into the prompt. This is
the core experiment: does prompt-time retrieval improve sentiment/emotion/
aspect accuracy over zero-shot or static few-shot?

Leakage is the responsibility of the caller — pass the test sample's fold
in `exclude_folds` so neither it nor its fold-mates leak into the context.
"""

from __future__ import annotations

from prompts.sentiment import build_system_prompt

from benchmark.dataset import Sample
from benchmark.retrieval.embedder import Embedder
from benchmark.retrieval.store import KNNStore, RetrievedExample


def _format_retrieved(r: RetrievedExample) -> str:
    return (
        f'Post: "{r.sample.text}"  [sim={r.similarity:.2f}]\n'
        f"  -> score={r.sample.gt.score}, "
        f"emotion={r.sample.gt.emotion}, aspect={r.sample.gt.aspect}"
    )


def build(
    sample: Sample,
    *,
    store: KNNStore,
    embedder: Embedder,
    k: int,
    brand_name: str | None = None,
    brand_industry: str | None = None,
    keywords: list[str] | None = None,
    exclude_folds: set[int] | None = None,
) -> tuple[str, list[int]]:
    """Build the prompt and return the list of exemplar post_ids used.

    The exemplar ids are returned so the runner can hash them into the cache
    key — different retrieved sets produce different cache cells.

    ``exclude_folds`` defaults to ``{sample.fold}`` so the retriever respects
    the eval dataset's k-fold split (the standard CV-leakage guard). Pass an
    empty set when ``store`` comes from a held-out exemplar dataset — folds
    in that file are meaningless relative to the eval split.
    """
    base = build_system_prompt(
        brand_name=brand_name,
        brand_industry=brand_industry,
        keywords=keywords,
    )
    if exclude_folds is None:
        exclude_folds = {sample.fold}
    retrieved = store.query(
        sample.text,
        embedder,
        k=k,
        exclude_post_ids={sample.post_id},
        exclude_folds=exclude_folds,
    )
    if not retrieved:
        return base, []
    block = "\n\n".join(_format_retrieved(r) for r in retrieved)
    prompt = (
        f"{base}\n\n"
        f"Labeled examples (most similar to the post you're about to analyze):\n"
        f"{block}"
    )
    return prompt, [r.sample.post_id for r in retrieved]
