"""Best-effort discovery of provider rate limits via response headers.

Groq (and other OpenAI-compatible APIs) stamp ``x-ratelimit-*`` headers on
responses that actually draw from the rate budget. We first try the cheap
``GET /models`` call; if that endpoint doesn't publish the headers (Groq
doesn't — it isn't rate-limited), we fall back to a single minimal
``chat.completions`` call so the probe still works.

The probe is fault-tolerant: any failure (network error, missing header,
unparseable value) falls through to the configured defaults.
"""

import logging
import re
from dataclasses import dataclass

from openai import AsyncOpenAI

logger = logging.getLogger(__name__)

# Go-style duration: optional h, m, s components. e.g. "7m59.56s", "1h30m", "45s".
_DURATION_RE = re.compile(
    r"^(?:(\d+(?:\.\d+)?)h)?(?:(\d+(?:\.\d+)?)m)?(?:(\d+(?:\.\d+)?)s)?$"
)


@dataclass
class DiscoveredLimits:
    rpm: int | None = None
    tpm: int | None = None

    @property
    def empty(self) -> bool:
        return self.rpm is None and self.tpm is None


async def discover_limits(
    raw_client: AsyncOpenAI, model: str | None = None
) -> DiscoveredLimits:
    """Probe the provider for rate limits and parse x-ratelimit-* headers.

    Tries ``GET /models`` first (free). If no usable headers come back and a
    ``model`` is given, falls back to a minimal ``chat.completions`` call
    (1 output token) so we get the headers that matter.
    """
    result = await _probe_models(raw_client)
    if not result.empty:
        return result

    if model is None:
        return result

    logger.info(
        "rate-limit headers absent from /models, falling back to a minimal chat probe"
    )
    return await _probe_chat(raw_client, model)


async def _probe_models(raw_client: AsyncOpenAI) -> DiscoveredLimits:
    try:
        response = await raw_client.models.with_raw_response.list()
    except Exception as e:
        logger.info("rate-limit probe (/models) skipped: %s", e)
        return DiscoveredLimits()
    return _parse_headers(response.headers, source="/models")


async def _probe_chat(raw_client: AsyncOpenAI, model: str) -> DiscoveredLimits:
    try:
        response = await raw_client.chat.completions.with_raw_response.create(
            model=model,
            messages=[{"role": "user", "content": "ping"}],
            max_tokens=1,
            temperature=0,
        )
    except Exception as e:
        logger.info("rate-limit probe (chat.completions) failed: %s", e)
        return DiscoveredLimits()
    return _parse_headers(response.headers, source="chat.completions")


def _parse_headers(headers, source: str) -> DiscoveredLimits:
    limit_requests = _parse_int(headers.get("x-ratelimit-limit-requests"))
    limit_tokens = _parse_int(headers.get("x-ratelimit-limit-tokens"))
    reset_requests = _parse_duration(headers.get("x-ratelimit-reset-requests"))
    reset_tokens = _parse_duration(headers.get("x-ratelimit-reset-tokens"))

    logger.info(
        "rate-limit headers from %s: limit_requests=%s reset=%ss, limit_tokens=%s reset=%ss",
        source,
        limit_requests,
        reset_requests,
        limit_tokens,
        reset_tokens,
    )

    rpm, rpm_note = _classify_rpm(limit_requests, reset_requests)
    tpm, tpm_note = _classify_tpm(limit_tokens, reset_tokens)
    logger.info("interpreted: %s | %s", rpm_note, tpm_note)

    return DiscoveredLimits(rpm=rpm, tpm=tpm)


def _parse_int(v: str | None) -> int | None:
    if v is None:
        return None
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


def _parse_duration(v: str | None) -> float | None:
    """Parse either a plain-seconds number or a Go-style '7m59.56s' string."""
    if v is None:
        return None
    v = v.strip()
    if not v:
        return None
    try:
        return float(v)
    except ValueError:
        pass
    m = _DURATION_RE.match(v)
    if not m:
        return None
    hours, minutes, seconds = m.groups()
    total = 0.0
    if hours:
        total += float(hours) * 3600
    if minutes:
        total += float(minutes) * 60
    if seconds:
        total += float(seconds)
    return total or None


# Per-minute buckets should reset fully within 60s. Give a little slack (up
# to 120s) for providers that round up or occasionally let the bucket drain
# below full at the window boundary.
_PER_MINUTE_RESET_MAX_S = 120.0

# Even without a reset header, a per-minute request budget well into the
# thousands is implausible for current providers — much more likely a daily
# quota being reported.
_RPM_MAGNITUDE_CEILING = 2000


def _classify_rpm(
    limit: int | None, reset_seconds: float | None
) -> tuple[int | None, str]:
    if limit is None:
        return None, "rpm: header missing"

    # Strong signal: the reset value is the refill time. A 345s reset on
    # groq/compound means "one more request token in ~6 minutes" — that's
    # the 250-per-day bucket, not a per-minute one.
    if reset_seconds is not None and reset_seconds > _PER_MINUTE_RESET_MAX_S:
        return (
            None,
            f"rpm: {limit} with reset={reset_seconds:.1f}s indicates a "
            "longer-than-minute window — skipping, using configured default",
        )

    # Fallback when reset is absent: just magnitude.
    if reset_seconds is None and limit >= _RPM_MAGNITUDE_CEILING:
        return (
            None,
            f"rpm: {limit} looks like a daily quota (>= {_RPM_MAGNITUDE_CEILING}, "
            "no reset header to confirm) — skipping, using default",
        )

    # Magnitude sanity check even when reset is small.
    if limit >= _RPM_MAGNITUDE_CEILING:
        return (
            None,
            f"rpm: {limit} is too large to be per-minute — skipping, using default",
        )

    return limit, f"rpm: {limit}/min"


def _classify_tpm(
    limit: int | None, reset_seconds: float | None
) -> tuple[int | None, str]:
    if limit is None:
        return None, "tpm: header missing"
    if reset_seconds is not None and reset_seconds > _PER_MINUTE_RESET_MAX_S:
        return (
            None,
            f"tpm: {limit} with reset={reset_seconds:.1f}s is a longer window — "
            "skipping, using default",
        )
    return limit, f"tpm: {limit}/min"
