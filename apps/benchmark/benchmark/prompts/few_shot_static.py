"""Static few-shot — append a fixed set of labeled examples.

The exemplars are chosen once (e.g. by stratified sampling across the four
aspects) and reused for every test post. Cheaper than retrieval but ignores
the relationship between the test post and the chosen examples.
"""

from __future__ import annotations

from prompts.sentiment import build_system_prompt

from benchmark.dataset import Sample


def _format_example(s: Sample) -> str:
    return (
        f'Post: "{s.text}"\n'
        f"  -> score={s.gt.score}, emotion={s.gt.emotion}, aspect={s.gt.aspect}"
    )


def build(
    sample: Sample,
    *,
    exemplars: list[Sample],
    brand_name: str | None = None,
    brand_industry: str | None = None,
    keywords: list[str] | None = None,
) -> str:
    del sample  # static examples don't depend on the test sample
    base = build_system_prompt(
        brand_name=brand_name,
        brand_industry=brand_industry,
        keywords=keywords,
    )
    if not exemplars:
        return base
    block = "\n\n".join(_format_example(e) for e in exemplars)
    return f"{base}\n\nLabeled examples:\n{block}"
