"""Zero-shot prompt — direct passthrough to the production prompt.

This is the baseline. The whole point of benchmarking *this system* is that
zero-shot uses the exact same `build_system_prompt` the production service
calls.
"""

from __future__ import annotations

from prompts.sentiment import build_system_prompt

from benchmark.dataset import Sample


def build(
    sample: Sample,
    *,
    brand_name: str | None = None,
    brand_industry: str | None = None,
    keywords: list[str] | None = None,
) -> str:
    del sample  # zero-shot doesn't peek at the sample
    return build_system_prompt(
        brand_name=brand_name,
        brand_industry=brand_industry,
        keywords=keywords,
    )
