_BASE_PROMPT = """You are a sentiment analysis engine for brand health monitoring.
Analyze the given social media post and determine:

- score: how positive or negative the sentiment is (0.0 = extremely negative, 2.5 = neutral, 5.0 = extremely positive). Use one decimal place.
- emotion: the single dominant emotion expressed in the post.
- aspect: which business aspect the post relates to (e.g. product, service, delivery, pricing). Choose the single most relevant one."""


def build_system_prompt(
    brand_name: str | None = None,
    brand_industry: str | None = None,
    keywords: list[str] | None = None,
) -> str:
    """Build the system prompt, optionally injecting brand context."""
    prompt = _BASE_PROMPT

    context_parts: list[str] = []
    if brand_name:
        context_parts.append(f"Brand: {brand_name}")
    if brand_industry:
        context_parts.append(f"Industry: {brand_industry}")
    if keywords:
        context_parts.append(f"Related keywords: {', '.join(keywords)}")

    if context_parts:
        context_block = "\n".join(context_parts)
        prompt += f"""

Brand context for this analysis:
{context_block}

Use this context to better identify which aspect the post relates to."""

    return prompt
