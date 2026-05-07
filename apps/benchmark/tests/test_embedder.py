"""FireworksEmbedder unit tests.

The OpenAI client is replaced with a fake before any real HTTP would happen,
so these run offline. We assert: (1) the call shape matches what the
Fireworks-compatible endpoint expects, (2) the returned vectors are
L2-normalized so cosine reduces to a dot product (KNNStore's contract),
(3) batching splits long inputs.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pytest

from benchmark.retrieval.embedder import (
    FIREWORKS_DEFAULT_MODEL,
    FireworksEmbedder,
    SBERTEmbedder,
    make_embedder,
)


@dataclass
class _FakeEmbeddingItem:
    embedding: list[float]
    index: int


@dataclass
class _FakeEmbeddingResponse:
    data: list[_FakeEmbeddingItem]


class _FakeEmbeddingsAPI:
    def __init__(self) -> None:
        self.calls: list[dict] = []

    def create(self, *, model: str, input: list[str], encoding_format: str) -> _FakeEmbeddingResponse:
        self.calls.append({"model": model, "input": list(input), "encoding_format": encoding_format})
        # Deterministic 3-D vectors keyed off text length so different texts
        # land at different points; index reversed to verify the embedder
        # re-sorts by `index`.
        items = []
        for i, text in enumerate(input):
            v = [float(len(text)), float(i + 1), 1.0]
            items.append(_FakeEmbeddingItem(embedding=v, index=i))
        items.reverse()  # SDK doesn't guarantee order — make sure embedder sorts
        return _FakeEmbeddingResponse(data=items)


class _FakeOpenAI:
    def __init__(self) -> None:
        self.embeddings = _FakeEmbeddingsAPI()


def _install_fake(embedder: FireworksEmbedder, fake: _FakeOpenAI) -> None:
    """Bypass `_ensure_client` so no real client / api key is required."""
    embedder._client = fake
    embedder._http_client = object()  # sentinel, never touched in encode()


def test_encode_returns_l2_normalized_float32():
    fake = _FakeOpenAI()
    e = FireworksEmbedder(name="test-model", api_key="unused")
    _install_fake(e, fake)

    vecs = e.encode(["hello", "world!"])

    assert vecs.shape == (2, 3)
    assert vecs.dtype == np.float32
    norms = np.linalg.norm(vecs, axis=1)
    assert np.allclose(norms, 1.0, atol=1e-5)
    assert e.dim == 3


def test_encode_calls_endpoint_with_expected_payload():
    fake = _FakeOpenAI()
    e = FireworksEmbedder(name="nomic-ai/nomic-embed-text-v1.5", api_key="unused")
    _install_fake(e, fake)

    e.encode(["alpha", "beta"])

    assert len(fake.embeddings.calls) == 1
    call = fake.embeddings.calls[0]
    assert call["model"] == "nomic-ai/nomic-embed-text-v1.5"
    assert call["input"] == ["alpha", "beta"]
    assert call["encoding_format"] == "float"


def test_encode_batches_when_inputs_exceed_batch_size():
    fake = _FakeOpenAI()
    e = FireworksEmbedder(name="m", api_key="unused", batch_size=2)
    _install_fake(e, fake)

    vecs = e.encode(["a", "b", "c", "d", "e"])

    assert vecs.shape == (5, 3)
    # 5 inputs, batch_size=2 → ceil(5/2) = 3 calls.
    assert len(fake.embeddings.calls) == 3
    assert [c["input"] for c in fake.embeddings.calls] == [["a", "b"], ["c", "d"], ["e"]]


def test_encode_resorts_response_by_index():
    """Embedder must restore input order even if the SDK returns out-of-order."""
    fake = _FakeOpenAI()
    e = FireworksEmbedder(name="m", api_key="unused")
    _install_fake(e, fake)

    # The fake reverses items; the embedder must put them back in input order.
    # Pre-normalize what we expect: input "ab" → raw [2, 1, 1], "cdef" → [4, 2, 1].
    vecs = e.encode(["ab", "cdef"])
    # First row should derive from "ab" (length 2), second from "cdef" (length 4).
    # After normalize, the relative ordering of the first component is preserved.
    assert vecs[0, 0] < vecs[1, 0]


def test_encode_empty_input_is_a_no_op():
    fake = _FakeOpenAI()
    e = FireworksEmbedder(name="m", api_key="unused")
    _install_fake(e, fake)

    out = e.encode([])
    assert out.shape == (0, 0)
    assert len(fake.embeddings.calls) == 0


def test_make_embedder_factory_returns_correct_type():
    sbert = make_embedder("sbert", None)
    fw = make_embedder("fireworks", None)
    assert isinstance(sbert, SBERTEmbedder)
    assert isinstance(fw, FireworksEmbedder)
    assert fw.name == FIREWORKS_DEFAULT_MODEL
    with pytest.raises(ValueError, match="unknown embedder provider"):
        make_embedder("openai", None)
