---
name: hawa-frontend
description: "Hawa frontend app. React 19, Vite 7, TypeScript. Build/test commands, project structure, patterns & conventions."
risk: unknown
source: project
date_added: "2026-03-25"
---

# Frontend — CLAUDE.md

> Development guide for the Hawa frontend application.

---


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

## 1. Stack

| Layer | Technology |
|-------|-----------|
| **Framework** | React 19 with TypeScript (~5.9) |
| **Build tool** | Vite 7 (`@vitejs/plugin-react` with Babel for Fast Refresh) |
| **Package manager** | npm |
| **Styling** | Plain CSS (component-scoped + global `index.css`) |
| **Linting** | ESLint 9 (flat config) with `typescript-eslint`, `react-hooks`, `react-refresh` |

---

## 2. Commands

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start dev server (Vite) |
| `npm run build` | Type-check (`tsc -b`) then build for production |
| `npm run lint` | ESLint across all `.ts`/`.tsx` files |
| `npm run preview` | Preview production build locally |
| `npx react-doctor` | Scan for security, performance, correctness & architecture issues (0–100 score) |
| `npx react-doctor --diff main` | Scan only files changed vs main branch |
| `npx react-doctor --verbose` | File-level details per rule |

All commands run from `apps/frontend/`.

---

## 3. Project Structure

```
src/
├── main.tsx        # Entry point, renders <App />
├── App.tsx         # Root component
├── App.css         # App-scoped styles
├── index.css       # Global styles (light/dark mode via media queries)
└── assets/         # Static assets (SVGs, images)
```

---

## 4. Patterns & Conventions

### Component Rules

| Rule | Detail |
|------|--------|
| Component style | Functional only — hooks, no class components |
| State | `useState` for local state; no global state library currently |
| API calls | Native `fetch` with `async/await` and `try/catch/finally` |
| Loading/error | Track with boolean state, render conditionally |

### TypeScript

| Setting | Value |
|---------|-------|
| Strict mode | Enabled |
| Implicit `any` | Not allowed |
| Unused locals/params | Not allowed |
| Fallthrough in switch | Not allowed |

---

## 5. Build & Type Checking

| Aspect | Detail |
|--------|--------|
| Type check | `tsc -b` runs before `vite build` — errors block production builds |
| Target | ES2022 |
| Module | ESNext |
| JSX | `react-jsx` (automatic transform) |
| Output | `dist/` (gitignored) |

---

## 6. API Integration

| Setting | Value |
|---------|-------|
| Backend URL | `http://localhost:8080` |
| HTTP client | Native `fetch` |
| Pattern | `async/await` with `try/catch/finally` |
| Error handling | Boolean error state with fallback UI |

---

> **Remember:** React is about composition. Build small, combine thoughtfully.

## 7. UI/UX

- **Always use the `ui-ux-pro-max` skill** when building, designing, reviewing, or improving any UI component or page.
- **Always read `DESIGN_SYSTEM.md`** before writing any UI code to ensure colors, typography, spacing, and component patterns are consistent with the approved design.
