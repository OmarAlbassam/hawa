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
| LLM Service | `uvicorn main:app --reload --port 8001` | 8001 | `apps/llm/` |

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

## Git Commit Conventions
Always use Conventional Commits format when committing: `<type>(<scope>): <description>`

### Types
| Type | When to use |
|---|---|
| `feat` | New feature or user-facing functionality |
| `fix` | Bug fix |
| `chore` | Maintenance, dependencies, config changes |
| `refactor` | Code restructuring without behavior change |
| `docs` | Documentation only |
| `test` | Adding or fixing tests |
| `perf` | Performance improvements |
| `ci` | CI/CD pipeline changes |

### Scopes for Hawa
Use one of the following scopes that matches what was changed:

`auth` `analysis` `reports` `brands` `posts` `dashboard` `llm` `reddit` `dataset` `feedback` `queue` `db` `ui` `config` `ci` `docker`

### Description Rules
- Lowercase, no period at the end
- Under 72 characters
- Use imperative tense ("add" not "added", "fix" not "fixed")

### Commit Rules
- Never use `git add .` to stage everything at once
- Group related file changes into separate logical commits
- Never bundle unrelated changes into a single commit
- If a change touches DB schema, commit the schema and its migration together

### Examples
```
feat(analysis): add start analysis flow with config form
feat(llm): implement multi-dimensional sentiment prompt
feat(reddit): collect posts using PRAW with keyword filtering
feat(reports): add CSV export for analyzed report data
feat(dataset): support custom CSV and XLSX dataset upload
feat(dashboard): add sentiment distribution pie chart
feat(posts): add filtering by emotion, aspect, and date range
feat(brands): add brand and keyword management page
fix(llm): correct emotion detection for sarcastic posts
fix(queue): handle timeout on long-running analysis jobs
fix(reports): fix incorrect confidence aggregation formula
chore(deps): upgrade drizzle-orm to latest version
chore(config): add environment variables for Reddit API keys
refactor(analysis): extract post preprocessing into separate service
test(llm): add unit tests for sentiment score normalization
docs(claude): add git commit conventions section
```

### How to Commit at End of Session
When asked to commit, always:
1. Read the changed files and understand what was built
2. Group files into logical, related chunks
3. Stage and commit each group separately with a conventional message
4. Show the proposed commits before running them and wait for approval
5. DO NOT INCLUDE YOUR WATERMARK IN THE COMMIT