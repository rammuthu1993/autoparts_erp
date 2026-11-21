# Stage 5 â€” Consolidated Evaluation Report

**Report version:** 0.2
**Audit date:** 2026-11-21
**Status:** Automated evaluation assets are present; recorded regression is green; operational and production gates remain open.

## 1. Baseline evidence from the uploaded project

The uploaded archive contains Maven Surefire text reports for the latest recorded project build:

| Metric | Recorded result |
|---|---:|
| Tests | 852 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

These are pre-existing build artifacts in the ZIP. They are not a fresh Maven execution performed during this review. The review environment has Java 21 and no Maven executable, while this project declares Java 25. A fresh Java 25 run is therefore required on the user's Windows environment.

The archive also contains the following Phase 5-focused report entries:

| Test suite | Recorded tests | Failures / errors |
|---|---:|---:|
| `ErpAiDomainEvaluationTest` | 152 | 0 / 0 |
| `ErpAiConcurrencyLoadTest` | 1 | 0 / 0 |
| `ErpAiSqlInjectionDefenseTest` | 1 | 0 / 0 |
| `ErpAiSecurityAuthorizationTest` | 8 | 0 / 0 |
| `ErpAiAsyncSecurityIsolationTest` | 4 | 0 / 0 |
| `PromptInjectionDefenseTest` | 10 | 0 / 0 |
| `KnowledgeRetrievalEvaluationTest` | 29 | 0 / 0 |

The suite counts above are included in the 852 recorded tests and must not be added to that total again.

## 2. Existing implementation confirmed in source

The uploaded project already contains substantial work for the eleven workstreams:

1. **Domain evaluation:** a 150-scenario JSON dataset and dynamic classifier tests, including deterministic read-only mutation rejection.
2. **Conversation evaluation:** conversation context resolution, pending clarification storage, comparison follow-ups, entity carry-forward, and ambiguity tests.
3. **Security:** immutable execution context, backend role enforcement, async isolation tests, and SQL parameter-binding coverage.
4. **Prompt injection:** untrusted knowledge-context handling, context-tag sanitization, history exclusion from grounded prompts, and citation validation.
5. **Retrieval:** PostgreSQL FTS retrieval engine, query preprocessing, access-control filtering, ranking/evaluation tests, and live grounded-answer evaluation infrastructure.
6. **Concurrency:** per-user request limiting, per-JVM execution admission, timeout/cancellation handling, and burst/concurrency tests.
7. **Performance:** Micrometer timers exist for request, provider, node, and tool duration. Representative HTTP/provider/database p95 evidence is not present.
8. **Token measurement:** Spring AI usage metadata is recorded where returned by the provider. Missing provider usage metadata must remain explicitly unknown; the history-budget byte heuristic is not exact token counting.
9. **Evaluation reporting:** a live grounded-answer evaluator already produces per-case and aggregate quality/usage information. The existing report is updated by this overlay to distinguish recorded tests from unmeasured operational gates.
10. **Production readiness:** existing Stage 4 controls and deployment notes exist, but database-role, migration, audit persistence, metrics access, and deployment checks remain open.
11. **Regression:** 852 recorded Surefire tests pass in the supplied archive; a fresh Java 25 full regression remains required.

## 3. Phase 5 gates still open

The following work cannot honestly be marked complete from source inspection or mocked tests alone:

- Fresh Java 25 `clean test` execution on the target Windows environment.
- Live PostgreSQL verification of the dedicated AI reader role, grants, row-level/tenant boundaries if applicable, and the existing Flyway migration history.
- Successful, denied, failed, and cancelled tool audit-row verification against the actual database.
- Authenticated Actuator metrics verification in the intended deployment profile.
- Representative HTTP-level multi-user load testing through the actual application and provider path.
- Separate p95 measurements for database/tool, provider, and end-to-end chat latency.
- Live Sarvam grounded-answer evaluation using an explicitly authorized run and real provider usage metadata.
- Multi-instance rate-limit/concurrency coordination before horizontal scaling; current controls are per JVM.
- Production migration reconciliation for the documented V29/V30 dependency before a fresh production deployment.

Do not infer any of these from a passing unit test or the recorded 852-test baseline.

## 4. Architecture constraints preserved

This review and overlay do not replace or reconfigure:

- LangGraph4j 1.9.7 orchestration.
- Spring AI integration.
- Spring JDBC / PostgreSQL data access.
- Existing graph state, execution context, tool authorization, timeout, cancellation, and audit architecture.

No Flyway migration is included in this overlay. Do not run Flyway `clean` or `repair` to bypass the documented V29/V30 reconciliation dependency.

## 5. Verification

From the Windows project root:

```powershell
# Full focused + full regression; requires Java 25.
.\scripts\phase5-verify.ps1

# Only focused deterministic Phase 5 tests.
.\scripts\phase5-verify.ps1 -FocusedOnly

# Or run manually:
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean test
```

The script validates Java 25, runs focused deterministic tests, then runs the full clean Maven regression unless `-FocusedOnly` is supplied. It aggregates Surefire XML results into `target/phase5-verification-summary.txt`.

## 6. Production readiness decision

**NOT APPROVED / NOT YET MEASURED.**

The source and recorded regression show substantial implementation and automated coverage. Production approval remains blocked on the operational, live-provider, representative-load, database-security, and migration gates above. This report intentionally does not manufacture latency, token, cost, security-leak, or provider-quality measurements.
