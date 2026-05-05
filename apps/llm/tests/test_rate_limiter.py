import asyncio

import pytest

from services.rate_limiter import ProviderRateLimiter


async def test_unthrottled_limiter_is_instant():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    start = asyncio.get_running_loop().time()
    for _ in range(10):
        await limiter.acquire()
    elapsed = asyncio.get_running_loop().time() - start
    assert elapsed < 0.05, f"unthrottled acquire should be near-zero, got {elapsed}s"


async def test_rpm_limiter_spreads_bursts():
    # Shrink window to 1s so the test is fast: 3 tokens per second, burst 6.
    # First 3 fire immediately; next 3 must wait ~1s for the bucket to refill.
    limiter = ProviderRateLimiter(rpm=3, window_seconds=1.0)
    start = asyncio.get_running_loop().time()
    await asyncio.gather(*[limiter.acquire() for _ in range(6)])
    elapsed = asyncio.get_running_loop().time() - start
    assert elapsed >= 0.8, f"expected throttling, got {elapsed}s"


async def test_notify_rate_limited_pauses_waiters():
    limiter = ProviderRateLimiter(rpm=0, tpm=0)
    limiter.notify_rate_limited(0.3)
    assert limiter.is_paused

    start = asyncio.get_running_loop().time()
    await limiter.acquire()
    elapsed = asyncio.get_running_loop().time() - start
    # Expected ~0.3s; allow slack for scheduler jitter.
    assert 0.25 <= elapsed <= 1.0, f"pause duration off: {elapsed}s"
    assert not limiter.is_paused


async def test_later_pause_extends_existing_pause():
    limiter = ProviderRateLimiter()
    limiter.notify_rate_limited(0.2)
    limiter.notify_rate_limited(0.6)  # later expiry — should win

    start = asyncio.get_running_loop().time()
    await limiter.acquire()
    elapsed = asyncio.get_running_loop().time() - start
    assert elapsed >= 0.5, f"second notification should have extended pause: {elapsed}s"


async def test_shorter_pause_does_not_shrink_existing_pause():
    limiter = ProviderRateLimiter()
    limiter.notify_rate_limited(0.5)
    limiter.notify_rate_limited(0.1)  # earlier expiry — ignored

    start = asyncio.get_running_loop().time()
    await limiter.acquire()
    elapsed = asyncio.get_running_loop().time() - start
    assert elapsed >= 0.4, f"first notification's expiry should still apply: {elapsed}s"


async def test_concurrent_waiters_all_resume_after_pause():
    limiter = ProviderRateLimiter()
    limiter.notify_rate_limited(0.2)

    async def wait() -> float:
        start = asyncio.get_running_loop().time()
        await limiter.acquire()
        return asyncio.get_running_loop().time() - start

    elapsed = await asyncio.gather(*[wait() for _ in range(5)])
    for e in elapsed:
        assert e >= 0.15


@pytest.mark.parametrize("retry_after", [0.0, 0.1, 0.5])
async def test_notify_with_various_retry_afters(retry_after):
    limiter = ProviderRateLimiter()
    limiter.notify_rate_limited(retry_after)
    await limiter.acquire()  # should not hang


# ---------------------------------------------------------------------------
# refund_tokens: returns over-reserved TPM budget after actual usage is known.
# ---------------------------------------------------------------------------


async def test_refund_tokens_lowers_bucket_level():
    limiter = ProviderRateLimiter(tpm=1000)
    await limiter.acquire(estimated_tokens=800)
    level_before = limiter._tpm_limiter._level
    assert level_before >= 800

    limiter.refund_tokens(500)
    assert limiter._tpm_limiter._level == max(0.0, level_before - 500)


async def test_refund_tokens_no_op_when_tpm_disabled():
    limiter = ProviderRateLimiter(rpm=10, tpm=0)
    # Should not raise.
    limiter.refund_tokens(500)


async def test_refund_tokens_clamps_at_zero():
    limiter = ProviderRateLimiter(tpm=1000)
    await limiter.acquire(estimated_tokens=100)
    limiter.refund_tokens(99999)
    assert limiter._tpm_limiter._level == 0.0


async def test_refund_lets_subsequent_acquire_proceed_immediately():
    """The realistic flow: a worker reserves N, the response comes back
    using only M < N, the worker calls refund(N - M), and the *next* call
    to acquire() sees the freed headroom without waiting for natural leak.
    (aiolimiter's already-sleeping waiters keep their original schedule —
    that's an aiolimiter API limitation, not what we rely on. Workers
    acquire fresh after releasing their concurrency-semaphore slot.)"""
    limiter = ProviderRateLimiter(tpm=1000, window_seconds=60.0)
    # First worker reserves 900, then refunds 800 once response is back.
    await limiter.acquire(estimated_tokens=900)
    limiter.refund_tokens(800)

    started = asyncio.get_running_loop().time()
    await asyncio.wait_for(limiter.acquire(estimated_tokens=500), timeout=0.5)
    elapsed = asyncio.get_running_loop().time() - started
    # Without refund, only 100/1000 cap was free; 500 needs 400 more tokens
    # to leak in, which at 1000/60s = ~24s. Must be near-instant.
    assert elapsed < 0.1

