"""Provider-agnostic rate limiting for outbound LLM calls.

Single gatekeeper that every LLM request passes through. Combines a
proactive token bucket (RPM and optional TPM) with a shared pause gate
that is flipped closed when any worker observes a 429 and reopens after
the server's Retry-After duration has elapsed.
"""

import asyncio
import logging

from aiolimiter import AsyncLimiter

logger = logging.getLogger(__name__)


class ProviderRateLimiter:
    def __init__(
        self,
        *,
        rpm: int = 0,
        tpm: int = 0,
        window_seconds: float = 60.0,
    ) -> None:
        self.configured_rpm = rpm
        self.configured_tpm = tpm
        self._rpm_limiter = (
            AsyncLimiter(rpm, window_seconds) if rpm > 0 else None
        )
        self._tpm_limiter = (
            AsyncLimiter(tpm, window_seconds) if tpm > 0 else None
        )
        self._available = asyncio.Event()
        self._available.set()
        self._resume_task: asyncio.Task | None = None
        self._resume_at_loop_time: float = 0.0

    async def acquire(self, estimated_tokens: int = 0) -> None:
        """Block until a request can be sent under the current budget."""
        # Fast path: no buckets configured and no pause in effect.
        if self._rpm_limiter is None and self._tpm_limiter is None and self._available.is_set():
            return

        # The pause gate is checked in a loop: acquiring the bucket can take
        # non-trivial time, during which a 429 could land and close the gate.
        while True:
            await self._available.wait()
            if self._rpm_limiter is not None:
                await self._rpm_limiter.acquire()
            if self._tpm_limiter is not None and estimated_tokens > 0:
                # AsyncLimiter caps amount at max_rate; clamp defensively.
                amount = min(estimated_tokens, self._tpm_limiter.max_rate)
                await self._tpm_limiter.acquire(amount)
            if self._available.is_set():
                return
            # Gate closed while we were waiting on a bucket — loop and wait.

    def refund_tokens(self, amount: int) -> None:
        """Return unused tokens to the TPM bucket after a response arrives.

        ``acquire(estimated_tokens)`` reserves a worst-case budget upfront —
        ``(prompt+text)/4 + max_tokens`` — but actual completions usually
        spend far less. Without a refund, every over-reservation permanently
        narrows the bucket, so steady-state throughput collapses to
        ``tpm / estimated_tokens`` even though the provider only charged
        ``tpm / actual_tokens``.

        ``aiolimiter.AsyncLimiter`` exposes no public 'give back' API, so
        we adjust its internal level directly. The level represents tokens
        currently consumed in the rolling window; clamping at 0 keeps the
        bucket from dropping below empty.
        """
        if self._tpm_limiter is None or amount <= 0:
            return
        # Touching aiolimiter internals — this is the supported workaround
        # since the library has no refund API. Pin the dependency so an
        # upstream rename is caught at install time, not at runtime.
        current = getattr(self._tpm_limiter, "_level", None)
        if current is None:
            return
        self._tpm_limiter._level = max(0.0, current - float(amount))

    def notify_rate_limited(self, retry_after_seconds: float) -> None:
        """Pause all acquirers for retry_after_seconds.

        Concurrent notifications do not compound — the latest expiry wins.
        """
        retry_after_seconds = max(0.0, float(retry_after_seconds))
        loop = asyncio.get_running_loop()
        resume_at = loop.time() + retry_after_seconds

        # If a pause is already scheduled to end later, keep it.
        if not self._available.is_set() and resume_at <= self._resume_at_loop_time:
            return

        self._available.clear()
        self._resume_at_loop_time = resume_at

        if self._resume_task is not None and not self._resume_task.done():
            self._resume_task.cancel()

        logger.warning(
            "rate limit hit — pausing all workers for %.2fs", retry_after_seconds
        )
        self._resume_task = loop.create_task(self._resume_after(retry_after_seconds))

    async def _resume_after(self, delay: float) -> None:
        try:
            await asyncio.sleep(delay)
        except asyncio.CancelledError:
            return
        self._available.set()
        logger.info("rate limit pause lifted — resuming")

    @property
    def is_paused(self) -> bool:
        return not self._available.is_set()


def null_limiter() -> ProviderRateLimiter:
    """An unthrottled limiter for providers/tests that don't need rate shaping."""
    return ProviderRateLimiter(rpm=0, tpm=0)
