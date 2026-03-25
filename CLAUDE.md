# Hawa

LLM-powered sentiment analysis system that extracts multi-dimensional insights (sentiment scores, emotions, brand aspects) from social media posts to support data-driven brand management.

## Project Overview

Hawa is a capstone project (IS498) that helps marketing teams analyze brand health from social media data. The system collects posts (from Reddit via PRAW, or manual CSV uploads), processes them through an LLM, and presents results in an interactive dashboard.

### Core Capabilities

- **Sentiment classification** — score from 0.0 (very negative) to 5.0 (very positive)
- **Emotion detection** — dominant emotion per post (joy, anger, sadness, etc.)
- **Aspect-based analysis** — identifies what the post is about (product, service, delivery, pricing, etc.)
- **Brand Status indicator** — aggregated score/label for overall brand health
- **Dashboard & reporting** — visualizations, filtering (date, sentiment, aspect, language), CSV export
- **Dual input** — automated social media collection or manual dataset upload

### User Roles

- **Marketing Team User** — initiates analysis, reviews results, provides feedback on misclassifications
- **System Administrator** — manages user accounts, permissions, monitors system analytics

## Monorepo Structure

```
hawa/
├── apps/
│   ├── backend/          # Spring Boot 4.0.3 REST API (Java 25, Maven)
│   ├── frontend/         # React 19 SPA (TypeScript, Vite 7)
│   └── llm/              # LLM service (sentiment analysis engine)
├── diagrams/             # Architecture & UX diagrams (draw.io)
└── PROJECT_DOC.md        # Full capstone project documentation
```

Each app has its own `CLAUDE.md` with app-specific conventions and instructions.

## System Architecture

```
Frontend (React) <--REST--> Backend (Spring Boot) <--> Database (PostgreSQL)
                                    |
                                    ├── Job Queue (internal)
                                    ├── LLM Service (sentiment analysis)
                                    └── Post Provider API (Reddit/PRAW)
```

- **Frontend** communicates with Backend via REST APIs
- **Backend** manages data, orchestrates analysis jobs, and interfaces with external services
- **LLM Service** reads jobs from an internal queue and processes posts for sentiment/emotion/aspect
- **Database** stores posts, analysis results, user accounts, and reports

## Running the Full Stack

| App | Command | Port | Directory |
|-----|---------|------|-----------|
| Backend | `./mvnw spring-boot:run` | 8080 | `apps/backend/` |
| Frontend | `npm run dev` | 5173 | `apps/frontend/` |

Frontend proxies API calls to `http://localhost:8080`. Backend has CORS configured for `http://localhost:5173`.

## General Conventions

### API Contract

- Backend exposes REST endpoints under `/api/` prefix
- Frontend consumes them using native `fetch` with `async/await`
- JSON request/response format throughout
- Consistent error response structure from backend

### Attribution

- Do not include attribution to Claude in commit messages or co-author attribution.

### Code Quality

- No hardcoded secrets — use environment variables
- Validate at system boundaries (user input, external APIs)
- Handle errors gracefully with clear messages

### Languages

- Primary: English
- Arabic support: exploratory/future scope
