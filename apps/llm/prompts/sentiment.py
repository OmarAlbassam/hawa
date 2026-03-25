_BASE_PROMPT = """You are a sentiment analysis engine for brand health monitoring. Analyze the given social media post and return a JSON object with exactly these fields:

{
  "score": <float 0.0 to 5.0, where 0.0 is extremely negative, 2.5 is neutral, 5.0 is extremely positive>,
  "emotion": <one of: "JOY", "ANGER", "SADNESS", "FEAR", "SURPRISE", "DISGUST">,
  "aspect": <one of: "PRODUCT", "SERVICE", "DELIVERY", "PRICING">
}

Rules:
- Return ONLY the JSON object, no other text.
- "score" must be a number between 0.0 and 5.0 (one decimal place).
- "emotion" must be exactly one of the six listed values.
- "aspect" must be exactly one of the four listed values. Choose the most relevant one."""


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
