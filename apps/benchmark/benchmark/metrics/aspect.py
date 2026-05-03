"""Aspect-specific metric helpers.

The dataset uses a closed taxonomy (the production `Aspect` enum), so the
core metric is plain classification — see `metrics.classification`. This
module just normalizes model output (case, whitespace, common synonyms) to
the canonical taxonomy *before* metrics are computed, so the comparison is
fair if the LLM produces "products" or "service quality" instead of the
canonical "PRODUCT" / "SERVICE".
"""

from __future__ import annotations

import re

from benchmark.dataset import ASPECT_LABELS

_NORMALIZE_RE = re.compile(r"[^a-z]+")

# Common LLM phrasings that map to the canonical taxonomy. Extend as you see
# new outputs in the wild — anything not in this map AND not in the canonical
# set falls through as "OTHER" for metric purposes.
_SYNONYMS: dict[str, str] = {
    "product": "PRODUCT",
    "products": "PRODUCT",
    "productquality": "PRODUCT",
    "qualityofproduct": "PRODUCT",
    "service": "SERVICE",
    "services": "SERVICE",
    "customerservice": "SERVICE",
    "support": "SERVICE",
    "delivery": "DELIVERY",
    "shipping": "DELIVERY",
    "shippingspeed": "DELIVERY",
    "logistics": "DELIVERY",
    "price": "PRICING",
    "pricing": "PRICING",
    "cost": "PRICING",
    "value": "PRICING",
    "valueformoney": "PRICING",
}

OTHER = "OTHER"


def normalize_aspect(raw: str | None) -> str:
    """Map a free-form LLM aspect string to the canonical taxonomy.

    Returns one of `ASPECT_LABELS` or `OTHER` when no match is found.
    """
    if not raw:
        return OTHER
    upper = raw.strip().upper()
    if upper in ASPECT_LABELS:
        return upper
    key = _NORMALIZE_RE.sub("", raw.lower())
    return _SYNONYMS.get(key, OTHER)
