"""Hawa benchmarking harness.

Reuses the production LLM service's `AnalyzerService`, `LLMClient`, and prompts
to evaluate models, prompt variants, temperatures, and few-shot strategies
against a hand-labeled control dataset.
"""

__version__ = "0.1.0"
