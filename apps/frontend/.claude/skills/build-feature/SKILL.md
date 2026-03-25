---
name: build-feature
description: "Scaffold and build a frontend feature following Hawa's React/TypeScript conventions. Use when the user wants to create or develop a new frontend component, page, or feature."
argument-hint: "[feature-description]"
disable-model-invocation: true
user-invocable: true
effort: high
---

# Frontend Feature Development

You are building a feature for the Hawa frontend — a React 19 SPA (TypeScript, Vite 7).

## Step 1: Gather Requirements

If the user provided a description via `$ARGUMENTS`, use it as a starting point. Otherwise, ask for one.

Then, before writing any code, ask the user the following (skip any that are already clear from the description):

1. **Feature type** — Is this a page, a reusable component, or a feature module (page + components)?
2. **UI description** — What should the user see and interact with? (layout, key elements, actions)
3. **Data** — What data does this feature display or collect? What are the fields and types?
4. **API endpoints** — Which backend endpoints does it consume? (method, path, request/response shape). If the backend isn't built yet, define the expected contract.
5. **State** — Any complex state logic? Multiple loading/error states? Form handling?
6. **Styling notes** — Any specific layout or styling requirements? (The project uses plain CSS, component-scoped.)

Present a summary of your understanding and get confirmation before proceeding.

## Step 2: Build the Feature

Once confirmed, generate the feature files under `apps/frontend/src/` following these conventions:

### File Organization
```
src/
├── config/
│   └── api.ts               # API base URL config (single source of truth)
├── components/
│   ├── ErrorBanner/          # Shared error display component
│   │   ├── ErrorBanner.tsx
│   │   └── ErrorBanner.css
│   └── {ComponentName}/
│       ├── {ComponentName}.tsx
│       └── {ComponentName}.css
├── pages/
│   └── {PageName}/
│       ├── {PageName}.tsx
│       └── {PageName}.css
├── types/
│   ├── page.ts              # Generic Page<T> type (matches Spring's Page)
│   └── {feature}.ts
├── services/
│   └── {feature}Service.ts
└── router.tsx                # Route definitions
```

### API Configuration (`config/api.ts`)

All services import the base URL from a single config file — never hardcode it.

```typescript
export const API_BASE_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8080";
```

If `config/api.ts` already exists, do not recreate it — just import from it.

### TypeScript Types (`types/{feature}.ts`)

- **Mirror backend DTOs exactly.** The backend defines `Create{Feature}Request`, `Update{Feature}Request`, and `{Feature}Response` DTOs — frontend interfaces must match their field names and types.
- Check the backend entity under `apps/backend/src/main/java/com/hawa/hawa_backend/{feature}/dto/` to confirm the shape.
- Map Java types: `Long` → `number`, `String` → `string`, `LocalDateTime` → `string` (ISO), enums → string union types.
- Define interfaces for component props separately.
- Use strict types — no `any`.
- Export all types for reuse.

```typescript
// Must match backend Create{Feature}Request DTO
export interface CreateReportRequest {
  brandId: number;
  dataSource: "REDDIT" | "CSV_UPLOAD";
}

// Must match backend {Feature}Response DTO
export interface ReportResponse {
  id: number;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  createdAt: string;
  updatedAt: string;
}

// Generic paginated response — matches Spring's Page<T> shape
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number; // current page (0-indexed)
  first: boolean;
  last: boolean;
  empty: boolean;
}
```

Define the `Page<T>` interface once in `types/page.ts` and import it wherever needed. Do not redefine it per feature.

### API Service (`services/{feature}Service.ts`)

- One function per API call
- Native `fetch` with `async/await`
- Import `API_BASE_URL` from `config/api.ts` — never hardcode the URL
- **Endpoint paths:** `/api/{plural-kebab-case}` — must match the backend's `@RequestMapping` (e.g., `/api/reports`, `/api/sentiment-reports`)
- Type the return values explicitly
- Throw on non-ok responses — extract the `message` field from the backend's `ApiError` response body (`{ status, message, timestamp }`)
- **List endpoints return paginated data.** All backend list endpoints return Spring's `Page<T>`. Accept `page` and `size` params, return `Page<{Feature}Response>`.

```typescript
import { API_BASE_URL } from "../config/api";
import { ReportResponse, CreateReportRequest } from "../types/report";
import { Page } from "../types/page";

export async function getReports(page = 0, size = 20): Promise<Page<ReportResponse>> {
  const response = await fetch(
    `${API_BASE_URL}/api/reports?page=${page}&size=${size}&sort=createdAt,desc`
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch reports");
  }
  return response.json();
}

export async function getReport(id: number): Promise<ReportResponse> {
  const response = await fetch(`${API_BASE_URL}/api/reports/${id}`);
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch report");
  }
  return response.json();
}

export async function createReport(data: CreateReportRequest): Promise<ReportResponse> {
  const response = await fetch(`${API_BASE_URL}/api/reports`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to create report");
  }
  return response.json();
}
```

### Error Handling

Use the shared `ErrorBanner` component to display errors consistently across all pages.

```typescript
// components/ErrorBanner/ErrorBanner.tsx
interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
}

const ErrorBanner = ({ message, onRetry }: ErrorBannerProps): JSX.Element => (
  <div className="ErrorBanner">
    <p className="ErrorBanner-message">{message}</p>
    {onRetry && (
      <button className="ErrorBanner-retry" onClick={onRetry}>
        Retry
      </button>
    )}
  </div>
);
```

Every page/component that fetches data must:
1. Track errors in state: `const [error, setError] = useState<string | null>(null)`
2. Catch service errors and store the message: `catch (err) { setError(err instanceof Error ? err.message : "Something went wrong") }`
3. Render `<ErrorBanner message={error} onRetry={loadData} />` when error is non-null
4. Clear the error on retry or new action: `setError(null)`

If `ErrorBanner` already exists, do not recreate it — just import from it.

### Form Handling

For features that create or update data (forms that map to backend `Create{Feature}Request` / `Update{Feature}Request`):

1. **Controlled inputs** — every field tracked with `useState`, typed to match the request DTO
2. **Validation** — validate on submit before calling the API. Show inline error messages next to invalid fields.
3. **Submission state** — track with three states:
   - `submitting: boolean` — disable the submit button and show a loading indicator
   - `error: string | null` — display in `<ErrorBanner>` above the form
   - On success — navigate to the list/detail page or show a success message
4. **Reset** — clear the form after successful submission

```typescript
const [form, setForm] = useState<CreateReportRequest>({ brandId: 0, dataSource: "REDDIT" });
const [errors, setErrors] = useState<Partial<Record<keyof CreateReportRequest, string>>>({});
const [submitting, setSubmitting] = useState(false);
const [submitError, setSubmitError] = useState<string | null>(null);

const validate = (): boolean => {
  const newErrors: typeof errors = {};
  if (!form.brandId) newErrors.brandId = "Brand is required";
  setErrors(newErrors);
  return Object.keys(newErrors).length === 0;
};

const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();
  if (!validate()) return;
  setSubmitting(true);
  setSubmitError(null);
  try {
    await createReport(form);
    // navigate or show success
  } catch (err) {
    setSubmitError(err instanceof Error ? err.message : "Submission failed");
  } finally {
    setSubmitting(false);
  }
};
```

### Components (`components/{Name}/{Name}.tsx`)
- Functional components only — arrow functions with explicit return types
- Props defined as TypeScript interfaces (in the component file if local, in `types/` if shared)
- `useState` for local state
- Use `useEffect` for data fetching on mount
- Keep components focused — split if doing too much

### CSS (`{Name}.css`)
- Component-scoped: class names prefixed with component name (e.g., `.ItemList-container`)
- Follow existing light/dark mode pattern from `index.css` (uses `prefers-color-scheme`)
- Responsive considerations for 320px+ screens

### Pages (`pages/{Name}/{Name}.tsx`)
- Compose from smaller components
- Handle top-level data fetching and pass data down as props
- Manage page-level loading/error states using the patterns above
- **List pages must handle pagination.** Track `page` in state, pass it to the service function, and render pagination controls using the `Page<T>` metadata:

```typescript
const [page, setPage] = useState(0);
const [data, setData] = useState<Page<ReportResponse> | null>(null);

const loadData = async () => {
  setLoading(true);
  setError(null);
  try {
    setData(await getReports(page));
  } catch (err) {
    setError(err instanceof Error ? err.message : "Something went wrong");
  } finally {
    setLoading(false);
  }
};

useEffect(() => { loadData(); }, [page]);

// In JSX:
// {data && !data.last && <button onClick={() => setPage(p => p + 1)}>Next</button>}
// {data && !data.first && <button onClick={() => setPage(p => p - 1)}>Previous</button>}
```

### Routing

When creating a new page, add its route to `src/router.tsx`. If `router.tsx` does not exist yet, create it using React Router:

```typescript
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App";

const AppRouter = () => (
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<App />} />
      {/* Add new routes here */}
    </Routes>
  </BrowserRouter>
);

export default AppRouter;
```

If `router.tsx` already exists, add the new route to the existing `<Routes>` block — do not recreate the file. Update `main.tsx` to render the router if it is not already doing so.

## Step 3: Verify

After generating all files:
1. Check the code compiles: `cd apps/frontend && npm run build`
2. Run the linter: `cd apps/frontend && npm run lint`
3. Run React Doctor: `cd apps/frontend && npx react-doctor`
4. Report the results (build, lint, health score) and list all created files with a brief summary of each.

If type-checking, linting, or React Doctor flags critical issues, fix them before reporting completion.

### React Doctor

React Doctor scans for security, performance, correctness, and architecture issues and outputs a 0–100 health score.

```bash
npx react-doctor              # Full scan
npx react-doctor --verbose    # File-level details per rule
npx react-doctor --diff main  # Only scan files changed vs main branch
npx react-doctor --score      # Score only (useful for CI)
```

Use `--diff main` when verifying a feature branch to only check new/changed files.
