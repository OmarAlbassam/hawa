"""Hawa benchmarking harness.

Reuses the production LLM service's `LLMClient`, prompts, and config to evaluate
models, prompt variants, temperatures, and few-shot strategies against a
hand-labeled control dataset.

apps/llm/ isn't structured as an installable package (it runs as a FastAPI app
with `apps/llm` itself on sys.path). To import its modules we add that
directory to sys.path here so any `from models import ...` / `from services.*`
imports throughout the benchmark resolve, regardless of the working directory
the runner is invoked from.
"""

from __future__ import annotations

import sys
from pathlib import Path

_LLM_DIR = Path(__file__).resolve().parents[2] / "llm"
if _LLM_DIR.is_dir() and str(_LLM_DIR) not in sys.path:
    sys.path.insert(0, str(_LLM_DIR))

__version__ = "0.1.0"
