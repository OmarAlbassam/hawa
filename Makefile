# ── Example Usage ──────────────────────────────────────────────
# make llm-setup        - create venv + install deps
# make llm-dev          - run the LLM service
# make llm-test         - run tests
# make frontend-dev     - run frontend
# make backend-dev      - run backend
# make dev              - run frontend + backend
# make dev-all          - run frontend + backend + llm
# make benchmark-setup  - create venv + install benchmark (with dev + notebooks)
# make benchmark-test   - run benchmark tests
# make benchmark-embed  - embed the control dataset
# make benchmark-run    - run experiments (override CONFIG=path/to.yaml)
# make benchmark-report - aggregate parquets into summary + plots
# make benchmark-viewer - launch Streamlit results viewer
# make benchmark-nb     - launch Jupyter Lab on the error-analysis notebook
# TODO: Run backend tests


# ── LLM Service ──────────────────────────────────────────────

llm-setup:
	cd apps/llm && python3 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt

llm-dev:
	cd apps/llm && . .venv/bin/activate && uvicorn main:app --reload --port 8001

llm-test:
	cd apps/llm && . .venv/bin/activate && pytest

## ── LLM Service - Commands──────────────────────────────────────────────

llm-health:
	cd apps/llm && . .venv/bin/activate && curl -X GET http://localhost:8001/health

# ── Frontend ─────────────────────────────────────────────────

frontend-setup:
	cd apps/frontend && npm install

frontend-dev:
	cd apps/frontend && npm run dev

# ── Backend ──────────────────────────────────────────────────

backend-dev:
	cd apps/backend && if [ -f .env ]; then set -a; . ./.env; set +a; fi; ./mvnw spring-boot:run

# ── Combined ─────────────────────────────────────────────────

dev:
	$(MAKE) frontend-dev & $(MAKE) backend-dev & wait

dev-all:
	$(MAKE) frontend-dev & $(MAKE) backend-dev & $(MAKE) llm-dev & wait

# ── Benchmark ────────────────────────────────────────────────
# Uses its own venv so the heavier deps (sentence-transformers, streamlit,
# jupyter) don't bleed into apps/llm. The benchmark imports apps/llm as a
# local source dep via pyproject's tool.uv.sources, so editing LLM code is
# picked up without reinstall.

CONFIG ?= configs/example.yaml

benchmark-setup:
	cd apps/benchmark && python3 -m venv .venv && . .venv/bin/activate && pip install -e ".[dev,notebooks]"

benchmark-test:
	cd apps/benchmark && . .venv/bin/activate && pytest

benchmark-embed:
	cd apps/benchmark && . .venv/bin/activate && benchmark embed

benchmark-run:
	cd apps/benchmark && . .venv/bin/activate && benchmark run $(CONFIG)

benchmark-report:
	cd apps/benchmark && . .venv/bin/activate && benchmark report

benchmark-viewer:
	cd apps/benchmark && . .venv/bin/activate && streamlit run viewer/app.py

benchmark-nb:
	cd apps/benchmark && . .venv/bin/activate && jupyter lab notebooks/error_analysis.ipynb
