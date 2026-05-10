# Divergence from IS498 Design

Where the implemented system differs from the IS498 figures (Figures 6–14). The code is the source of truth; the IS498 diagrams are stale.

## Workflow consolidations

### `generate-report` (IS498 Fig. 8) — merged into `start-analysis`

IS498 modelled report generation as a separate user-triggered step that aggregates analyses into an exportable file. The implementation has no separate "generate" step:

- Aggregation runs automatically inside `AnalysisJobRunner.run()` after the LLM batch completes (`finalizeCompleted` writes the aggregated `summary` and `score` onto the `Report` row).
- The user-facing "export" is CSV download from the posts view (`GET /api/reports/{id}/export`), which is shown inside `view-posts.mmd` as an `opt` block.

No standalone diagram is produced for this workflow.

### `view-aspects` — folded into the overview response; `view-status-indicator` lives on the Dashboard

IS498 modelled view-status-indicator and view-aspects as separate figures. In the current implementation:

- The aspect breakdown is **bundled into `ReportOverviewResponse`** (see `aspects: AspectBreakdownItem[]` field). `ReportStatus.tsx` makes a single overview call (`GET /api/reports/{id}`) that returns the score, emotion distribution, and per-aspect breakdown together. There is no separate `/aspects` fetch from this page anymore. A standalone `GET /api/reports/{id}/aspects` endpoint may still exist in the backend but is no longer consumed by the report-detail page.
- **Status indicator is not mounted on the report-detail page.** The `<StatusIndicator>` component is mounted on `Dashboard.tsx` and calls `GET /api/brands/{brandId}/status-indicator` for the brand the user has selected in the navbar context. This has its own diagram: `view-status-indicator.mmd`.
- A report-scoped variant of the endpoint (`GET /api/reports/{reportId}/status-indicator`) exists in the backend but is not currently called from any frontend page.

`view-posts` stays a separate diagram because it is a different page (`/reports/{id}/posts`) with its own filtering, pagination, sort logic, and CSV export. The report-detail page also lazily reuses the posts endpoint (with `relevance=IRRELEVANT`) when the user expands the "filtered-out posts" section — this appears as an `opt` block in `view-result.mmd`.

## Per-workflow renames and structural changes

| IS498 element | Implemented as | Note |
|---|---|---|
| "Job Queue" component | Spring `@Async("analysisExecutor")` thread pool | No external broker. `AnalysisJobRunner` runs in-process; jobs are tracked by `Report.status` (QUEUED → PROCESSING → COMPLETED/FAILED). |
| "Sentiment Analysis Service" sync call | `LlmClient` over HTTP `POST /analyze/batch` | Backend-to-LLM is synchronous HTTP; concurrency / rate-limiting lives inside the LLM service (`AnalyzerService` semaphore + `ProviderRateLimiter`). |
| "Reddit collector" as a separate actor | `PostCollector` (factory selects Reddit vs. CSV by `DataSourceEnum`) | Both Reddit and CSV-upload paths converge on the same `AnalysisJobRunner`. |
| "Generate Report" user action | Implicit, run by `AnalysisJobRunner.finalizeCompleted` | Aggregation is automatic; no separate user step. |
| "Status Indicator" as a brand-only view | Reusable component, both report-level (`/api/reports/{id}/status-indicator`) and brand-level (`/api/brands/{id}/status-indicator`) | Component `StatusIndicator` is dual-mode. |
| "View Result" not-found / processing alts | Polling loop on `/status` endpoint, then `getReportOverview` rejects with 400 if not `COMPLETED`, 404 if missing | Frontend uses `failureReason` from status response on `FAILED`. |
| Inaccurate-review feedback as a CRUD form | Upsert by `(userId, reviewId)`; second submission updates instead of inserting | `FeedbackService` retries on `DataIntegrityViolationException` for concurrent inserts. |
| XLSX upload | Not implemented | Only `.csv` accepted (client-side extension check + server-side `DatasetCsvParser`). XLSX support is deferred. |

## Deferred / not implemented

- **XLSX dataset upload** — UI accepts `.csv` only. Scope deferred from IS498.
- **Standalone "Generate Report" flow** — collapsed into `start-analysis`. No separate user trigger.
- **Async messaging / external job queue** — replaced by Spring `@Async` in-process executor. If the system later needs durability across restarts, this is the layer that will change.

## Authorization

IS498 diagrams omit auth entirely. The implementation enforces company-level scoping at the service layer on every read and write — see `CONVENTIONS.md`. This is not redrawn per diagram.
