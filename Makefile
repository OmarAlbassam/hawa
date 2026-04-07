# ── Example Usage ──────────────────────────────────────────────
# make llm-setup    - create venv + install deps
# make llm-dev      - run the LLM service
# make llm-test     - run tests
# make frontend-dev - run frontend
# make backend-dev  - run backend
# make dev          - run frontend + backend
# make dev-all      - run frontend + backend + llm
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
	cd apps/backend && ./mvnw spring-boot:run

# ── Combined ─────────────────────────────────────────────────

dev:
	$(MAKE) frontend-dev & $(MAKE) backend-dev & wait

dev-all:
	$(MAKE) frontend-dev & $(MAKE) backend-dev & $(MAKE) llm-dev & wait

