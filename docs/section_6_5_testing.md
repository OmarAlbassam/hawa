# 6.5 Testing

This section describes how Hawa is tested across its three runtime services. The model-quality evaluation work is treated as a separate testing track and is covered in detail in Chapter 5.

## 6.5.1 Testing Methodology

Hawa was developed with a test-driven approach: features were specified by tests written alongside or in advance of the production code, and the resulting suite is treated as the executable definition of correct behavior.

The backend strategy, formalized in `apps/backend/docs/testing_strategy.md`, is built around feature tests that run against the full Spring application context using `MockMvc` and a real PostgreSQL database supplied by Testcontainers. Exercising the real context, rather than the lighter `@WebMvcTest` or `@DataJpaTest` slices, guarantees that filters, Spring Security, and JSON serialization behave exactly as they do in production. The LLM service is the only external dependency that is mocked, using a Mockito stub so tests remain deterministic and require no network access.

The LLM service is tested with `pytest`, with the OpenAI-compatible SDK mocked using `unittest.mock`. Coverage targets the deterministic logic surrounding the model (schema validation and enum coercion, the RPM and TPM rate limiter, the 429 retry and pause-gate, and the startup quota-discovery probe) rather than the model's own outputs, which are the benchmark's concern.

The frontend has no automated test harness; its primary user flows are verified manually before each demo.

## 6.5.2 Test Plan and Coverage

Hawa's test plan is expressed directly in code rather than as a parallel TC-numbered document. Test classes are named and organized so that the controller test for each feature serves as the behavioral specification for that feature's endpoints. The remainder of this subsection summarizes what exists by component.

**Backend (Spring Boot).** 212 `@Test` methods across 18 test classes, all passing on the head of the main branch, weighted toward the most user-facing surfaces: `ReportControllerTest` (46), `AdminControllerTest` (42), `BrandControllerTest` (21), and `AuthFlowTest` (20). Coverage spans happy-path flows together with the standard HTTP failure modes (401, 403, 404, 409 on duplicate keys, 400 on validation errors). Multi-tenancy isolation receives particular attention: `AnalysisControllerTest`, `BrandControllerTest`, `SubmitFeedbackTest`, and `StartAnalysisFromCsvTest` each set up an `otherCompany` fixture and assert that cross-tenant access is rejected. `AnalysisStartupRecoveryTest` covers the recovery path that fail-cleans any analysis left in a `PROCESSING` or `QUEUED` state when a previous process exited.

**LLM service (FastAPI).** Approximately 120 test methods across 8 files: `test_llm_retry` (35), `test_analyzer` (20), `test_limits_probe` (19), `test_enum_coercion` (16), `test_config` (13), `test_preprocessing` (7), `test_rate_limiter` (7), and `test_main` (3). The retry suite exhaustively walks the 429-handling state machine, and the limits-probe suite covers the auto-discovery of each provider's RPM and TPM ceilings from `x-ratelimit-*` response headers at startup.

**Frontend (React).** No automated tests. Manual verification covers login, brand creation, starting an analysis, viewing a report, and exporting posts as CSV.

**Benchmark (model-quality evaluation).** Covered in Chapter 5. It reports classification accuracy and macro-F1 on emotion and aspect, MAE and RMSE on the sentiment score, and McNemar's test with paired-bootstrap confidence intervals when comparing variants.
