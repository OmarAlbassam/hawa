from __future__ import annotations

from benchmark.cache import (
    CachedResult,
    CacheKey,
    ResultCache,
    hash_fewshot_ids,
    hash_prompt,
)


def _key(post_id: int = 1, prompt: str = "p", fewshot=()) -> CacheKey:
    return CacheKey(
        experiment_id="exp",
        model="m",
        prompt_hash=hash_prompt(prompt),
        temperature=0.0,
        post_id=post_id,
        fewshot_hash=hash_fewshot_ids(list(fewshot)),
    )


def test_cache_hit_returns_stored(tmp_path):
    cache = ResultCache(tmp_path / "c.db")
    key = _key()
    payload = CachedResult(
        is_relevant=True,
        irrelevance_reason=None,
        pred_score=4.2,
        pred_emotion="JOY",
        pred_aspect="PRODUCT",
        error=None,
        latency_ms=120.0,
    )
    cache.put(key, payload)
    got = cache.get(key)
    assert got is not None
    assert got.pred_score == 4.2
    assert got.pred_emotion == "JOY"


def test_cache_miss_on_different_prompt(tmp_path):
    cache = ResultCache(tmp_path / "c.db")
    cache.put(
        _key(prompt="A"),
        CachedResult(True, None, 4.0, "JOY", "PRODUCT", None, 1.0),
    )
    assert cache.get(_key(prompt="B")) is None


def test_cache_miss_on_different_fewshot(tmp_path):
    cache = ResultCache(tmp_path / "c.db")
    cache.put(
        _key(fewshot=(1, 2, 3)),
        CachedResult(True, None, 4.0, "JOY", "PRODUCT", None, 1.0),
    )
    assert cache.get(_key(fewshot=(1, 2, 4))) is None


def test_cache_count_matches_inserts(tmp_path):
    cache = ResultCache(tmp_path / "c.db")
    for pid in range(5):
        cache.put(
            _key(post_id=pid),
            CachedResult(True, None, 4.0, "JOY", "PRODUCT", None, 1.0),
        )
    assert cache.count("exp") == 5


def test_fewshot_hash_order_independent():
    assert hash_fewshot_ids([3, 1, 2]) == hash_fewshot_ids([1, 2, 3])


def test_fewshot_hash_empty_is_stable():
    assert hash_fewshot_ids([]) == "none"
