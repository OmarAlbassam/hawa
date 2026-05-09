# Sequence Diagram Conventions

Conventions shared by every diagram in this directory. Stated once here, omitted from individual diagrams to keep them readable.

## Multi-tenancy: companyId scoping

Every authenticated request resolves a `companyId` via `AuthenticatedUserService.getCompanyId()`. Service-layer methods enforce that all repository queries are scoped to that company:

- **`AnalysisService.startAnalysis` / `startAnalysisFromCsv`** — validate `brand.companyId == authenticated companyId` before creating the report.
- **`ReportService.listReports`** — `findByCompanyIdWithFilters(companyId, ...)` always includes `companyId` in the WHERE clause.
- **`ReportService.getReportOverview` / `listPosts` / `getStatusIndicator` / `getAspectBreakdown`** — repository projections accept `(reportId, companyId)`; a row from another company returns empty → 404.
- **`FeedbackService.submitFeedback`** — walks `review → post → report → brand → company` and rejects if not the caller's company.

Diagrams do not draw this check on every arrow. Assume it runs at the service entry point.

## Omitted from all diagrams

- JWT validation, Spring Security filter chain, role checks
- Logging, metrics, tracing, exception handlers, framework internals
- DTO ↔ entity mapping
- Trivial `@Valid` / JPA constraint validation (only domain validation is shown)
- Axios / fetch wrapper as a separate participant — collapsed to the `«view»` component
- Service-to-service chains that do not change the workflow

## Participant naming

- Stereotypes use guillemets: `«view»`, `«controller»`, `«service»`, `«repository»`, `«async worker»`, `«client»`, `«external»`.
- View names match the React page/component (`StartAnalysis`, `ReportStatus`, `PostsList`, `ReportList`, `InaccurateAnalysisDialog`).
- Controller / Service / Repository names match the Java class names exactly.

## Arrow conventions

- Frontend → Controller arrows show `HTTP_METHOD /api/path`.
- Service → Repository arrows show the Spring Data method name.
- Solid arrow (`->>`) for calls, dotted (`-->>`) for returns.
- HTTP responses use `200 OK`, `400`, `404`, `202` etc. on the dotted return arrow.
