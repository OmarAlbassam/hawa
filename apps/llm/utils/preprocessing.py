import re


_URL_PATTERN = re.compile(r"https?://\S+|www\.\S+")
_MENTION_PATTERN = re.compile(r"@\w+")
_MULTI_SPACE = re.compile(r"\s+")


def clean_text(text: str, max_length: int = 2048) -> str:
    """Clean social media text for LLM analysis.

    Strips URLs, @mentions, normalizes whitespace, and truncates.
    """
    text = _URL_PATTERN.sub("", text)
    text = _MENTION_PATTERN.sub("", text)
    text = _MULTI_SPACE.sub(" ", text)
    text = text.strip()

    if len(text) > max_length:
        text = text[:max_length]

    return text
