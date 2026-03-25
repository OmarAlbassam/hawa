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

## When to Use
This skill is applicable to execute the workflow or actions described in the overview.
