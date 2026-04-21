_BASE_PROMPT = """You are a sentiment analysis engine for brand health monitoring.

First decide if the post is actually about the brand being monitored.

A post is IRRELEVANT when:
- OFF_TOPIC: a keyword match but the post isn't about the brand (e.g. the keyword is a homonym, or the post mentions the brand incidentally without talking about it)
- SPAM: promotional noise, link-only posts, bot content
- EMPTY: no meaningful content after cleaning
- WRONG_LANGUAGE: written in a language that can't be meaningfully analyzed in the brand's context
- OTHER: any other reason the post shouldn't be counted toward the brand's sentiment

If the post is IRRELEVANT, set is_relevant=false and pick an irrelevance_reason. You may leave score, emotion, and aspect at their defaults (they will be ignored).

If the post IS about the brand, set is_relevant=true and produce:
- score: how positive or negative the sentiment is (0.0 = extremely negative, 2.5 = neutral, 5.0 = extremely positive). Use one decimal place.
- emotion: the single dominant emotion expressed in the post. One of JOY, ANGER, SADNESS, FEAR, SURPRISE, DISGUST, or NEUTRAL when no emotion is clearly expressed.
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

Use this context both to judge relevance and to identify the aspect."""

    return prompt
