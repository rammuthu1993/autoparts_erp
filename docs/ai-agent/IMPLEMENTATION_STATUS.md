# Auto Parts ERP AI Assistant: Implementation Status Ledger

*Last Updated*: 2026-11-17 (Stage 1 Completed; Stage 2A Completed)

---

## Executive Status Overview

| Stage | Title | Status | Gate Approval |
| :--- | :--- | :--- | :--- |
| **Stage 1** | Architecture Audit, State Schema Design & Traceability Setup | **COMPLETED** | Approved by User |
| **Stage 2A** | Read-Only Code & Schema Audit (Verified Implementation Map) | **COMPLETED** | **Awaiting Review of Audit Map** |
| **Stage 2B** | Product & Inventory Tools, Typed State & Immutable Security Context | **PENDING** | Awaiting Stage 2A Gate Approval |
| **Stage 2C** | Purchase, Sales, Customer, and Supplier Tools | **QUEUED** | Depends on Stage 2B |
| **Stage 2D** | Returns, Financial, and Reports Tools + Audit Logging | **QUEUED** | Depends on Stage 2C |
| **Stage 2E** | Hybrid Routing, Multi-Entity Context & Dynamic Synthesis | **QUEUED** | Depends on Stage 2D |
| **Stage 3** | Advanced AI Capabilities & Grounded Knowledge RAG | **QUEUED** | Depends on Stage 2E |
| **Stage 4** | Production Reliability, Security & Observability | **QUEUED** | Depends on Stage 3 |
| **Stage 5** | Testing, Evaluation & Production Readiness | **QUEUED** | Depends on Stage 4 |

---

## Stage 2A Completed Tasks (Read-Only Audit)

* [x] **Full Database Schema Audit**: Inspected Flyway migrations `V1` through `V30` to verify exact table names, column types, constraints, and sequences across all ERP domains.
* [x] **Repository & Service Inspection**: Inspected all 28 non-AI application repository interfaces and implementations (`ProductRepository`, `InventoryRepository`, `PurchaseRepository`, `SalesRepository`, `CustomerLedgerRepository`, `SupplierLedgerRepository`, `ExpenseLedgerRepository`, `DailyClosingRepository`, `AdvancedReportRepository`, etc.), plus the internal `AiRepository` / `AiRepositoryImpl` (29 interfaces and 29 implementations in total across the project).
* [x] **Discovered Exact Schema Realities**: Resolved discrepancies (e.g. `product_code`, `qty`, `minimum_stock`, `supplier_name`, `customer_code`, `customer_name`, `purchase_products`, `sales_products`, `difference_amount`).
* [x] **Designed Immutable Execution Context Safeguard**: Designed `ErpAiExecutionContext` (immutable record constructed on HTTP request thread) ensuring graph state is **not** the sole authorization authority and preventing model-generated state updates from tampering with authorization.
* [x] **Authored Verified Implementation Map**: Created comprehensive baseline document `docs/ai-agent/STAGE_2A_VERIFIED_IMPLEMENTATION_MAP.md`.
* [x] **Zero Code Changes Maintained**: Verified that zero Java code, SQL migrations, dependencies, or database schemas were modified in Stage 2A.

---

## Stage 1 Completed Tasks

* [x] **Verified LangGraph4j 1.9.7 Channel API**: Decompiled and inspected `Channels.java`, `BaseChannel.java`, and `AgentState.java` directly from `langgraph4j-core-1.9.7.jar`. Verified exact `Channels.base(Supplier)` and `Channels.base(Reducer, Supplier)` factory signatures.
* [x] **Verified Security Role Matrix**: Inspected custom security annotations (`@AdminOnly`, `@AdminOrManager`, `@SalesAccess`, `@AuthenticatedUser`) and verified actual role requirements across all services and controllers.
* [x] **Documented Full Business Question Coverage**: Created comprehensive coverage matrix across all 10 ERP domains with natural language variations, entity extraction rules, role requirements, and expected empty/error behavior.
* [x] **Established Strict Read-Only Policy**: Explicitly documented that all Stage 2 AI tools are read-only and that write operations are rejected with clean guidance.
* [x] **Recorded Flyway V29/V30 Deferral**: Formally isolated migrations V29 and V30. Confirmed that no Flyway repair/clean/reset operations or V31 migrations will be executed during development.
* [x] **Captured Actual Maven Test Baseline**: Verified through live execution across all 13 active test classes on disk: **185 tests total, 184 passed, 0 failures, 1 PostgreSQL authentication error** in `PostgresQueryTimeoutIntegrationTest`.
* [x] **Created Persistent Documentation**:
  * `docs/ai-agent/ARCHITECTURE.md`
  * `docs/ai-agent/BUSINESS_QUESTION_COVERAGE.md`
  * `docs/ai-agent/IMPLEMENTATION_PLAN.md`
  * `docs/ai-agent/IMPLEMENTATION_STATUS.md`
  * `docs/ai-agent/STAGE_2A_VERIFIED_IMPLEMENTATION_MAP.md`

---

## Actual Maven Test Baseline Results

Captured from live verified execution on Java 25.0.3 LTS with Maven 3.10.0 (Surefire XML Reports):

| Test Class | Category | Tests Run | Passed | Failures | Errors | Status |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `ErpAiGraphNodesTest` | AI Graph Nodes Unit Test | 55 | 55 | 0 | 0 | **PASSED** |
| `ErpAiIntentClassifierTest` | AI Intent Classifier Test | 42 | 42 | 0 | 0 | **PASSED** |
| `ErpAiAgentServiceConcurrencyTest` | AI Concurrency & Timeout Test | 22 | 22 | 0 | 0 | **PASSED** |
| `ProductLookupToolTest` | AI Product Lookup Unit Test | 16 | 16 | 0 | 0 | **PASSED** |
| `AiAssistantServiceTest` | AI Assistant Service Test | 15 | 15 | 0 | 0 | **PASSED** |
| `ErpAiComparisonAndContextTest` | AI Context Resolution Test | 11 | 11 | 0 | 0 | **PASSED** |
| `AuthenticationFlowTests` | Security Flow Integration Test | 6 | 6 | 0 | 0 | **PASSED** |
| `InventoryServiceImplTests` | Inventory Service Unit Test | 5 | 5 | 0 | 0 | **PASSED** |
| `AdvancedReportServiceImplTests` | Reports Service Unit Test | 4 | 4 | 0 | 0 | **PASSED** |
| `GlobalExceptionHandlerTest` | Global Exception Handler Test | 4 | 4 | 0 | 0 | **PASSED** |
| `ProductLookupToolComparisonTest` | AI Product Comparison Test | 3 | 3 | 0 | 0 | **PASSED** |
| `AiRepositoryImplStatusTest` | AI Repository Unit Test | 1 | 1 | 0 | 0 | **PASSED** |
| `PostgresQueryTimeoutIntegrationTest` | Live DB Timeout Integration Test | 1 | 0 | 0 | 1 | **ERROR (DB Auth)** |
| **TOTAL** | | **185** | **184** | **0** | **1** | **184 / 185 (99.46%)** |

*Note on Integration Test Error & Test Suite Discrepancy Reconciliation*:
1. `PostgresQueryTimeoutIntegrationTest` failed with `PSQLException: FATAL: password authentication failed for user "postgres"` because local test execution does not supply a live PostgreSQL password. All 184 unit and mock-based tests pass cleanly. In Stage 5, test fixtures will isolate integration tests with test profiles or testcontainers.
2. An earlier baseline draft recorded 121 tests across 17 suites because four test classes (`AiPerformanceMetricsTest`, `ErpAiPurchaseLookupTest`, `InventoryHistoryLookupToolTest`, `PurchaseLookupToolTest`) deleted in commits `dbf0a48`/`1e476e6` had stale XML reports lingering in `target/surefire-reports`. Concurrently, `ErpAiGraphNodesTest` had expanded from 4 to 55 tests, and `ErpAiAgentServiceConcurrencyTest` had expanded from 11 to 22 tests. Live execution confirms exactly 13 active test classes and 185 tests on disk.

---

## Approved Architectural Decisions Log

| Decision ID | Summary | Approved Strategy | Date |
| :--- | :--- | :--- | :--- |
| **DEC-001** | Flyway V29/V30 `ai_agent_runs` Conflict & Pre-Production Dependency | **DEFERRED**: Do not modify V29 or V30. Do not create V31 now. Do not run Flyway repair/clean. Use current development database. Production migration reconciliation for `ai_agent_runs` is a mandatory blocking prerequisite before deploying the complete application and V32 RAG migration to a fresh production database. | 2026-11-16 |
| **DEC-002** | Transactional Facts vs. RAG Boundary | **LIVE JDBC FOR FACTS**: Transactional data (stock, prices, purchases, sales, ledgers, debts) queried exclusively via Spring JDBC. RAG is reserved strictly for unstructured business documents. | 2026-11-16 |
| **DEC-003** | LangGraph4j State Channel Mechanism | **VERIFIED API & DESIGN SPECIFICATION (IMPLEMENTATION PENDING STAGE 2B)**: LangGraph4j 1.9.7 channel API signatures (`Channels.base(...)`) verified directly from JAR. Typed state schema design is formally documented in ARCHITECTURE.md and STAGE_2A_VERIFIED_IMPLEMENTATION_MAP.md. Java implementation of typed channels in `ErpAiGraphState.SCHEMA` (currently `Map.of()`) is pending Stage 2B. | 2026-11-16 |
| **DEC-004** | Security Context Propagation & Role Authorization | **BACKEND JAVA ENFORCEMENT & IMMUTABLE CONTEXT**: Authenticated identity and granted authorities are extracted from Spring Security on the HTTP request thread into an immutable, server-controlled `ErpAiExecutionContext` (defensive copy via `Set.copyOf()`, referencing DEC-010). The context is passed through a trusted server-side invocation path to protected tools, which validate authorization before executing JDBC queries. Graph state may carry workflow data, but graph state or LLM-generated content must never be the sole authority for access control. Roles and permissions are strictly prohibited from being accepted from user prompts or LLM output. Asynchronous authorization integration testing is mandatory in Stage 2. | 2026-11-16 |
| **DEC-005** | Read-Only Operational Policy | **STRICTLY READ-ONLY**: Stage 2 AI tools are strictly read-only. Mutation verbs ("create purchase", "adjust stock", "pay invoice") are intercepted and explained as unsupported. | 2026-11-16 |
| **DEC-006** | Concurrency & Timeout Control | **REUSE EXISTING ARCHITECTURE**: Reuse `ActiveExecution`, `timeoutScheduler`, `AiExecutionInProgressException`, and `CancellationException` in `ErpAiAgentService`. Avoid duplicate concurrency controls. | 2026-11-16 |
| **DEC-007** | RAG Search Engine Strategy | **HYBRID WITH FTS FALLBACK**: Primary vector search via pgvector; PostgreSQL Full-Text Search (`tsvector`/`tsquery`) as a keyword-based fallback if pgvector is unavailable. Probe embedding dimensions prior to schema creation. | 2026-11-16 |
| **DEC-008** | Pre-Implementation Schema & Repository Verification | **MANDATORY VERIFICATION**: All proposed database table names, column identifiers, foreign-key joins, repository methods, and financial calculations in the coverage matrix are provisional architectural targets and must be formally verified against the live PostgreSQL schema and repository classes before implementing each domain tool. | 2026-11-16 |
| **DEC-009** | Provisional Performance SLA Targets | **PROVISIONAL TARGETS**: SLA targets are provisional. Separate tracking is required for database/tool latency, LLM provider latency, and end-to-end latency. Production SLA compliance will not be claimed until measured under representative multi-user load in Stage 5. | 2026-11-16 |
| **DEC-010** | Immutable Execution Context Safeguard & Defensive Immutability | **DUAL-BARRIER ZERO-TRUST SECURITY & DEFENSIVE COPYING**: Graph state is NOT the sole authorization authority. Authenticated subject and authorities are captured on the HTTP request thread into an immutable `ErpAiExecutionContext` record with defensive copying (`Set.copyOf()`), non-null `conversationId` enforcement, and null/blank role filtering. Passed directly to tool invocation boundaries. Model-generated graph updates cannot alter authorization decisions. Tools validate roles independently prior to executing JDBC queries (asserting zero DB calls on unauthorized requests). | 2026-11-17 |
| **DEC-011** | Sub-staged Stage 2 Execution Strategy | **INCREMENTAL APPROVAL GATES**: Stage 2 is partitioned into 2A (Audit & Map), 2B (Products, Inventory & Security Context), 2C (Purchases, Sales, Customers, Suppliers), 2D (Returns, Financial, Reports & Audit), and 2E (Hybrid Routing, Context & Synthesis) with user approval between sub-stages. | 2026-11-17 |
| **DEC-012** | COGS and Profit Data Model Limitation | **UNAVAILABLE COGS & PROFIT EXPLANATION**: The current ERP data model does not track transaction-level historical COGS (`sales_products` captures selling price, not acquisition cost; `products.purchase_price` is mutable). Conforming to `AdvancedReportServiceImpl`, the AI assistant is strictly prohibited from calculating or presenting certified or indicative gross profit/margins. When queried on profitability or COGS, the assistant must explain this system limitation. Supported verified metrics are Sales Revenue (`sales` where `status = 'COMPLETED'`), Purchase Total (`purchases` where `status = 'RECEIVED'`), Customer/Supplier Ledgers, and Active Expenses. | 2026-11-17 |

---

## Deferred Tasks Ledger

| Task ID | Deferred Item | Justification & Strategy | Target Phase |
| :--- | :--- | :--- | :--- |
| **DEF-001** | Production Migration Reconciliation (`ai_agent_runs`) | Current development database has the table created manually. Modifying migrations now risks breaking development environments. A clean, idempotent migration reconciliation strategy will be prepared and executed prior to deploying the complete application and V32 RAG migration to a fresh production database. | Pre-Production Deployment |
| **DEF-002** | Controlled Write Operations (Mutations) | Transactional mutations (e.g. creating purchases, adjusting inventory) require multi-step human-in-the-loop approvals, idempotency keys, and explicit audit logging. Excluded from Stage 2 read-only scope. | Post-Release Roadmap |
| **DEF-003** | Distributed Rate Limiter (Redis) | Application currently deploys as a single backend instance. In-memory sliding window rate limiter meets all requirements without external infrastructure dependencies. | Multi-Instance Scaling |

---

## Known Issues and Technical Risks

1. **Host PostgreSQL 18 pgvector Availability**:
   * *Risk*: The target PostgreSQL instance may not have the native `vector` C extension installed.
   * *Mitigation*: Fallback to PostgreSQL native Full-Text Search (`tsvector`/`tsquery` with GIN indexing) ensures document retrieval works reliably in all environments.
2. **Sarvam AI Latency & Context Limits**:
   * *Risk*: High latency or rate limiting on external LLM calls.
   * *Mitigation*: Deterministic Java formatting for factual tabular queries bypasses the LLM completely, saving tokens and keeping p95 tool latency under 1,500ms.
3. **Database Integration Test Environment & Actual Baseline**:
   * *Risk*: `PostgresQueryTimeoutIntegrationTest` requires a running local PostgreSQL instance with matching credentials.
   * *Baseline Status*: Preserved actual baseline of **184 passed, 0 failures, 1 PostgreSQL authentication error (185 total across 13 suites)**. The full suite is explicitly NOT described as completely passing. In Stage 5, isolated test profiles or testcontainers will be configured.
4. **SecurityContextHolder Thread-Local Risk in Asynchronous Execution**:
   * *Risk*: LangGraph4j executes graph nodes asynchronously across worker threads, causing Spring Security's default `ThreadLocal` context to be lost.
   * *Mitigation*: Authenticated identity and authorities are extracted on the HTTP request thread in `AiAssistantController` / `AiAssistantService` and encapsulated in an immutable, server-controlled `ErpAiExecutionContext` (referencing DEC-010) passed directly to tool execution boundaries. Graph state is not the authorization authority. Roles from LLM prompts or chat input are strictly prohibited. Asynchronous authorization integration testing is mandatory in Stage 2.
5. **Schema & Repository Drift**:
   * *Risk*: Implementing domain tools based on presumed database schemas or financial calculations.
   * *Mitigation*: Mandatory pre-implementation verification of live PostgreSQL tables, columns, and repository methods before developing each domain tool in Stage 2.
6. **Absence of Historical Transaction-Level COGS**:
   * *Risk*: Users querying for gross profit, product profitability, or COGS assume the ERP tracks historical cost per sale.
   * *Mitigation*: Conforming to `AdvancedReportServiceImpl`, the AI assistant is strictly prohibited from computing certified or indicative profit/margins. It must transparently state that transaction-level historical acquisition costs are not tracked in the current data model.
