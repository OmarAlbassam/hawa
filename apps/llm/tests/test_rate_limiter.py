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
