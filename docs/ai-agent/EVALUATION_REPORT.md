# Stage 5 — Evaluation Report (Kickoff / In Progress)

**Report version:** 0.1
**Date:** 2026-11-21
**Status:** Evaluation scaffolding added; project-level Maven verification and production measurements pending.

## 1. Scope

Stage 5 evaluates the ERP AI assistant across the ten business domains in the implementation plan, then verifies security, concurrency, latency, token usage, and release readiness. This report deliberately separates deterministic automated tests from live-provider, live-database, and representative-load measurements.

## 2. Evaluation assets added

| Asset | Purpose | Scope / status |
|---|---|---|
| `src/test/resources/ai-evaluation/erp-ai-domain-evaluation.json` | Curated intent dataset | 150 scenarios, 15 per domain |
| `ErpAiDomainEvaluationTest` | Dataset integrity and deterministic classifier evaluation | Added; Maven execution pending |
| `ErpAiConcurrencyLoadTest` | Burst test for global execution admission | 40 simultaneous attempts; configured capacity 10; Maven execution pending |
| `ErpAiSqlInjectionDefenseTest` | Verify product identifier input remains a JDBC bind parameter | Added; Maven execution pending |
| `EVALUATION_REPORT.md` | Record evidence, gaps, and release gates | This document |

The ten dataset domains are Product & Inventory, Purchases, Sales, Customers, Suppliers, Returns, Financials, Reports, Audit Logs, and Knowledge. Entity-list requests without a search criterion are expected to clarify rather than silently return an unbounded result. Customer purchase-history phrasing is expected to resolve to the customer domain. Supplier purchasing queries are expected to use the supplier-filtered purchase operation.

## 3. Preliminary deterministic classifier check

The 150 dataset queries were executed against the current `ErpAiIntentClassifier` source in an isolated JDK 21 harness with lightweight stubs for the Spring stereotype and `StringUtils` dependencies. Result: **150/150 expected intents matched**. Seven read-only mutation examples also matched `UNSUPPORTED` in that harness.

This is a preliminary source-level check only. It is **not** a Maven/JUnit run, does not exercise the complete Spring application, and does not prove live database or LLM answer quality. The project targets Java 25; the available isolated harness runtime was Java 21.

## 4. Security evidence and remaining work

Existing project tests include:

- `ErpAiSecurityAuthorizationTest` — role enforcement and zero-JDBC authorization guards.
- `ErpAiAsyncSecurityIsolationTest` — cashier/manager context isolation through the graph path, including concurrent requests.
- `PromptInjectionDefenseTest` — retrieved knowledge is placed in an untrusted prompt section rather than trusted system instructions.
- `CitationValidatorTest` — exact citation validation.

The new SQL injection test verifies that a hostile product identifier is passed as a JDBC parameter and is not interpolated into SQL. All of these tests still need to be run together on the user's Java 25 project environment. Further adversarial cases are required for prompt-injection attempts against transactional tools, cross-user conversation ownership, role combinations across every protected domain tool, and empty/unauthorized RAG retrieval.

## 5. Concurrency and performance

`ErpAiConcurrencyLoadTest` stresses the existing per-JVM global admission semaphore with 40 simultaneous attempts against a capacity of 10. Its intended assertions are 10 admitted, 30 rejected, peak active permits of 10, and full permit recovery after completion. It is an admission-control stress test, **not** a representative end-to-end HTTP/graph/provider load benchmark.

No Stage 5 SLA measurement is claimed yet:

| Metric | Provisional target | Current evidence |
|---|---:|---|
| Database / tool query latency p95 | < 1,500 ms | Not measured under representative load |
| LLM provider latency p95 | < 2,500 ms | Not measured under representative load |
| End-to-end chat latency p95 | < 4,000 ms | Not measured under representative load |
| Intent classification accuracy | > 95% | Preliminary deterministic harness 150/150; full JUnit suite pending |
| Authorization leak rate | 0.0% | Existing automated security tests available; consolidated rerun pending |
| SQL injection vulnerability | 0.0% | Parameter-binding test added; execution pending |
| Concurrent active graph runs | 10 | Semaphore stress test added; execution pending |
| Token budget | < 2,500 tokens/request | History budget is an approximate UTF-8-byte heuristic; provider token usage depends on returned metadata; representative measurement pending |

Provider, tool, and end-to-end timings must be reported separately. Do not combine them into a single latency number or infer SLA compliance from unit-test timings.

## 6. Commands for project verification

Run from `D:\Projects\autoparts-erp` after applying the patch:

```powershell
.\mvnw.cmd "-Dtest=ErpAiDomainEvaluationTest,ErpAiConcurrencyLoadTest,ErpAiSqlInjectionDefenseTest,ErpAiSecurityAuthorizationTest,ErpAiAsyncSecurityIsolationTest,PromptInjectionDefenseTest" test
.\mvnw.cmd clean test
```

Then perform the non-test operational checks:

1. Verify V34 is applied in the intended development database; do not edit applied migrations and do not run Flyway `clean` or `repair`.
2. Verify audit rows for success, authorization denial, failure, and cancellation.
3. Run `docs/sql/verify-ai-tool-role.sql` as the dedicated AI reader and confirm least privilege, no inherited write permissions, and the column-only `users` grant.
4. Verify authenticated Actuator access and the AI metrics endpoint.
5. Verify HTTP 429 includes `Retry-After`.
6. Run a non-production multi-user load test through the HTTP API with a deterministic/mock provider and report the three latency tiers separately.
7. Run live-provider grounded-answer evaluation separately from deterministic mocked tests.

## 7. Release blockers and known limitations

- Stage 4 operational verification remains pending: V34, production AI-reader grants, audit persistence, and Actuator metrics.
- Stage 3C Phase 4 retrieval-preprocessing/fallback/ranking work remains queued in the implementation ledger.
- The Stage 3C live-provider grounded-answer quality evaluation remains separate from mocked regression.
- Production migration reconciliation for the existing V29/V30 `ai_agent_runs` history is a mandatory fresh-database deployment dependency.
- Rate limiting and global concurrency admission are in-memory/per-JVM. Multi-instance deployment requires shared coordination before claiming cluster-wide enforcement.
- Conversation history token budgeting is approximate; exact token compliance requires the configured model's tokenizer or authoritative provider usage metadata.

**Production readiness decision: NOT APPROVED / NOT YET MEASURED.** Stage 5 is in progress. No production SLA or security sign-off is claimed by this kickoff report.
