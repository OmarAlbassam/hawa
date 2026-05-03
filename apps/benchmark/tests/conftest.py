"""Shared pytest fixtures.

We need `apps/llm/` on sys.path so the benchmark package can `from models
import ...` etc, the same way the LLM service does at runtime.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LLM = ROOT.parent / "llm"

for p in (str(ROOT), str(LLM)):
    if p not in sys.path:
        sys.path.insert(0, p)
