# Auto Parts ERP AI Assistant: Five-Stage Implementation Plan

This document outlines the detailed roadmap for evolving the Auto Parts ERP AI agent into a production-grade, secure, and observable ERP assistant using LangGraph4j 1.9.7 and Spring AI 2.0.2.

---

## Strategic Principles & Constraints

1. **Orchestration**: LangGraph4j 1.9.7 remains the central orchestration framework.
2. **Transactional Truth**: All business-critical ERP facts (stock, prices, purchases, sales, debts, expenses) are retrieved through live parameterized Spring JDBC queries.
3. **Database Schema Verification**: All proposed database tables, columns, joins, repositories, and financial calculations must be verified against the actual PostgreSQL schema and existing repositories before implementing any domain tool. The coverage matrix defines architectural targets, not proof of existing schema.
4. **Knowledge Retrieval (RAG)**: RAG is strictly reserved for unstructured business documents (manuals, warranty policies, standard operating procedures). Transactional queries never query vector stores.
5. **Backend Security & Asynchronous Context**: Role-based access control is enforced in Java tool methods (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_CASHIER`), independent of LLM prompts. Because LangGraph4j executes asynchronously across worker threads, default `SecurityContextHolder` thread-local context is lost. To ensure fail-closed security, authenticated identity and authorities are extracted on the HTTP request thread in `AiAssistantController` / `AiAssistantService` into an immutable, server-controlled `ErpAiExecutionContext` (defensive copy via `Set.copyOf()`), passed through a trusted server-side invocation path to protected tools. Protected tools validate authorization before JDBC query execution. Graph state may carry workflow data, but is never the authorization authority. Under no circumstances may roles or permissions be accepted or derived from user prompts or LLM output.
6. **Strict Read-Only Policy**: Stage 2 tools are strictly read-only. Unintended mutations ("create purchase", "adjust stock") are deterministically rejected.
7. **Flyway Migration Isolation & Pre-Production Dependency**: Flyway migrations V29 and V30 (`ai_agent_runs`) are left untouched during development. No V31 or clean/repair operations will run now. However, production migration reconciliation for `ai_agent_runs` is a mandatory blocking dependency that must be resolved prior to deploying the complete application and V32 RAG migration to a fresh production database.
8. **Stage-by-Stage Control**: Work is executed strictly one stage at a time with explicit user review and approval between stages.

---

## Provisional Target SLAs (Subject to Representative Load Measurement)

> [!NOTE]
> All SLA targets defined below are **provisional targets**. Production SLA compliance cannot and will not be claimed until the assistant is benchmarked under representative load in Stage 5. Measurements must be tracked separately across three distinct latency tiers:
> 1. **Database / Tool Query Latency**: Time spent executing parameterized JDBC queries against PostgreSQL.
> 2. **LLM Provider Latency**: Time spent in external network inference calls to the AI model provider.
> 3. **End-to-End Chat Latency**: Total wall-clock time from incoming HTTP request to final validated response.

| Metric | Provisional Target SLA | Measurement Method |
| :--- | :--- | :--- |
| **Database / Tool Query Latency (p95)** | < 1,500 ms | Separate Micrometer `ai.tool.duration` timer |
| **LLM Provider Latency (p95)** | < 2,500 ms | Separate Micrometer `ai.provider.duration` timer |
| **End-to-End Chat Latency (p95)** | < 4,000 ms | Separate Micrometer `ai.request.duration` timer |
| **Intent Classification Accuracy** | > 95% on evaluation dataset | Automated assertions in `ErpAiDomainEvaluationTest` |
| **Authorization Leak Rate** | **0.0%** (Zero Tolerance) | Automated adversarial role tests (sync & async) |
| **SQL Injection Vulnerability** | **0.0%** (Zero Tolerance) | Parameterized JDBC assertion verification |
| **Concurrent Execution Capacity** | 10 concurrent active graph runs | Load benchmark with HikariCP pool monitoring |
| **Token Budget Compliance** | < 2,500 tokens per request | `ChatResponse.getMetadata().getUsage()` tracking |

---

## Detailed Stage Roadmaps

### STAGE 1: Documentation Baseline, State Schema Design & Traceability Setup
* **Objective**: Establish the persistent documentation framework in `docs/ai-agent/`, verify the LangGraph4j 1.9.7 channel API signatures, document the multi-domain Business Question Coverage Matrix, record the deferred migration notice, and establish the Maven test baseline.
* **Scope**: Documentation and baseline inspection ONLY. Zero source code, schema, or migration changes.
* **Implementation Tasks**:
  1. Author `docs/ai-agent/ARCHITECTURE.md` with verified `Channels.base()` signatures.
  2. Author `docs/ai-agent/BUSINESS_QUESTION_COVERAGE.md` covering all 10 ERP domains with mandatory database verification notice.
  3. Author `docs/ai-agent/IMPLEMENTATION_PLAN.md` with provisional SLA targets and distinct latency tiers.
  4. Author `docs/ai-agent/IMPLEMENTATION_STATUS.md` recording task status, test baseline, and deferred tasks.
  5. Inspect and document actual Maven test baseline: **185 total tests across 13 suites: 184 passed, zero failures, one PostgreSQL authentication error** in `PostgresQueryTimeoutIntegrationTest` (environment configuration issue). (The test suite is preserved with this exact baseline and not described as completely passing).
* **Deliverables**: Five markdown documents in `docs/ai-agent/` (including `STAGE_2A_VERIFIED_IMPLEMENTATION_MAP.md`).
* **Complexity**: Low.

---

### STAGE 2: ERP Business Intelligence and Dynamic Read-Only Tool Coverage
* **Objective**: Build modular, read-only Spring JDBC domain tools across all ERP modules, implement hybrid intent classification (deterministic fast-path + structured LLM classification fallback), multi-entity context resolution, typed LangGraph4j state channels, and role-based tool authorization.
* **Read-Only Scope**: Tools only read ERP data. Mutation requests ("create purchase", "adjust stock", "record payment") are rejected with a helpful explanation.
* **Sub-Staged Approval Gates**: To minimize blast radius across ERP modules, Stage 2 is partitioned into five distinct sub-stages:

#### STAGE 2A: Read-Only Code & Schema Audit (Verified Implementation Map) [COMPLETED]
* **Scope**: Read-only inspection of live database schema (`V1`–`V30`), existing service interfaces, repositories, and current AI tools. **Zero code, schema, or migration changes.**
* **Deliverable**: `docs/ai-agent/STAGE_2A_VERIFIED_IMPLEMENTATION_MAP.md` documenting verified column names, foreign keys, calculation formulas, existing AI hooks, and the immutable execution context safeguard.

#### STAGE 2B: Product & Inventory Tools, Typed State, and Immutable Security Context [AWAITING USER APPROVAL]
* **Scope**: Foundational security, defensive immutability, typed channels, and core catalog/inventory tools.
* **Core Architectural Principles for Stage 2B**:
  1. *Never Authorize from Graph State Alone*: Every protected tool method receives `ErpAiExecutionContext` through a server-controlled invocation path, independent of mutable graph state or LLM output.
  2. *Authorize Before JDBC Execution*: Authorization checks execute before opening JDBC statements; tests must assert zero database interactions on unauthorized requests.
  3. *Test Asynchronous Security Isolation*: Concurrently execute requests from different users with different roles (e.g. Cashier vs Manager); verify zero cross-execution security context leakage.
  4. *Preserve Existing Behavior*: Existing test suites for `ProductLookupTool` and `InventoryHistoryLookupTool` must continue passing after integration.
  5. *Careful Dead Code Removal*: Before deleting `AiToolRegistry.java`, verify zero usages across Spring configurations, properties, tests, reflection, and dependency injection.
  6. *Operation-Specific Permissions*: Keep permissions granular (Product search and current stock require `@SalesAccess`; inventory movement history and adjustments require `@AdminOrManager`).
* **Implementation Tasks**:
  1. Create `com.autoparts.erp.ai.security.ErpAiExecutionContext` with `Set.copyOf()` defensive immutability, non-null `conversationId`, and validated roles.
  2. Create `com.autoparts.erp.ai.security.ToolAuthorizationException` (typed security exception).
  3. Extract `authentication.getAuthorities()` in `AiAssistantController` / `AiAssistantService` on the HTTP thread and instantiate `ErpAiExecutionContext`.
  4. Implement typed channels in `ErpAiGraphState.java` using verified `Channels.base(...)` signatures.
  5. Refactor `ProductLookupTool` and `InventoryHistoryLookupTool` to validate `ErpAiExecutionContext` prior to JDBC queries.
  6. Add low-stock query tool (`getLowStockProducts()`) in inventory tools with `@SalesAccess` validation.
  7. Safely remove unreferenced prototype `AiToolRegistry.java`.
  8. Implement asynchronous security isolation tests and unit tests.
* **Deliverable**: Core tools, typed state, immutable security context, unit and async security tests.

#### STAGE 2C: Purchase, Sales, Customer, and Supplier Tools [QUEUED]
* **Scope**: Commercial ERP domain tools.
* **Implementation Tasks**:
  1. Implement `PurchaseAiTools.java` (`getPurchaseByNumber`, `getPurchasesBySupplier`, `getPurchasesByDateRange`, `getLastPurchasePrice`). Requires `@AdminOrManager`.
  2. Implement `SalesAiTools.java` (`getSaleByInvoiceNumber`, `getSalesByCustomer`, `getSalesSummaryByDateRange`). Requires `@SalesAccess`.
  3. Implement `CustomerAiTools.java` (`searchCustomers`, `getCustomerDetails`, `getCustomerOutstandingBalance`). Requires `@SalesAccess`.
  4. Implement `SupplierAiTools.java` (`searchSuppliers`, `getSupplierDetails`, `getSupplierPayablesSummary`). Requires `@SalesAccess` for contact, `@AdminOrManager` for debts.
* **Deliverable**: Commercial domain tool beans and unit tests.

#### STAGE 2D: Returns, Financial, and Reports Tools [QUEUED]
* **Scope**: Operations, financials, ledgers, and reporting.
* **Implementation Tasks**:
  1. Implement `ReturnAiTools.java` (sales returns, refunds, purchase returns, supplier credits).
  2. Implement `FinancialAiTools.java` (open cash register sessions, expenses by date, daily closings).
  3. Implement `ReportAiTools.java` (KPI summaries, ABC/XYZ inventory metrics, RFM customer cohorts, reconciliation discrepancies; enforces system limitation that transaction-level historical COGS and gross profit are unavailable).
  4. Instrument all tools with audit logging to `ai_tool_runs`.
* **Deliverable**: Operational & financial tool beans, audit logger, and unit tests.

#### STAGE 2E: Hybrid Routing, Multi-Entity Context, and Dynamic Synthesis [QUEUED]
* **Scope**: Intent classification, context resolution, and graph orchestration.
* **Implementation Tasks**:
  1. Upgrade `ErpAiIntentClassifier.java` for multi-domain queries and entity extraction.
  2. Upgrade `ErpAiConversationContextResolver.java` for multi-entity context resolution (Products, Invoices, Purchases, Customers, Suppliers).
  3. Refactor `ErpAiGraphNodes.java` to dynamically dispatch across all domain tools.
  4. Update `erp-system-prompt.st` with full ERP domain descriptions and injection delimiters.
  5. Execute full Stage 2 regression and evaluation test suite.
* **Deliverable**: Dynamic graph pipeline, context resolver, and comprehensive integration test suite.

---

### STAGE 3: Advanced AI Capabilities and Grounded Knowledge RAG
* **Objective**: Implement an enterprise RAG pipeline for unstructured business documentation (SOPs, warranty policies, return guidelines) using PostgreSQL vector capabilities with a robust Full-Text Search fallback, document access controls, and source citations.
* **Flyway Reconciliation Dependency**: Production migration reconciliation for `ai_agent_runs` (resolving the V29/V30 duplicate conflict) must be completed before deploying the complete application and the V32 RAG migration to a fresh production database. Migrations V29 and V30 are preserved without modification during development.
* **Vector Schema & Embedding Strategy**:
  1. Probe the configured provider (Sarvam AI / OpenAI) for embedding model availability and dimensions (`dim=1024` or `dim=1536`).
  2. Evaluate host PostgreSQL 18 environment for native `pgvector` extension.
  3. *Primary Engine*: PostgreSQL `pgvector` (`vector` column + HNSW index) via Spring AI pgvector integration.
  4. *Reliable Fallback Engine*: PostgreSQL Full-Text Search (`tsvector`/`tsquery` with GIN indexing) if `pgvector` is not compiled on the target PostgreSQL instance. (Note: Full-text search is explicitly treated as a keyword-based fallback, not an equivalent replacement for semantic embeddings).
* **Implementation Tasks**:
  1. Verify embedding model and pgvector availability.
  2. Create migration `V32__create_knowledge_tables.sql` (`knowledge_documents` and `knowledge_chunks` with metadata JSONB, required_role, version).
  3. Implement `KnowledgeIngestionService` (PDF/TXT/MD validation, 500-token chunking, 50-token overlap, metadata tagging).
  4. Implement `KnowledgeRetrievalService` (role-filtered retrieval, evaluation-driven similarity threshold, top-4 chunks).
  5. Integrate RAG step into LangGraph4j graph for procedural queries.
  6. Format grounded answers with visible Markdown citations (`[Source: Warranty Policy v1.2, §3]`).
  7. Strict routing assertion: Transactional queries NEVER invoke RAG.
* **Deliverables**: Migration `V32`, RAG services, knowledge controller, and integration tests.
* **Complexity**: High.

---

### STAGE 4: Production Reliability, Security and Observability
* **Objective**: Harden the assistant for high availability and observability by implementing a bounded provider retry policy with backoff and jitter, rate limiting, structured audit logging in `ai_tool_runs`, and Micrometer metrics.
* **Implementation Tasks**:
  1. Inspect and reuse existing concurrency (`ActiveExecution`, `ConcurrentHashMap`) and cancellation mechanisms in `ErpAiAgentService`.
  2. Implement `AiResilienceService`:
     * Max 3 retry attempts for transient errors (HTTP 429, 502, 503, 504, connection reset).
     * Exponential backoff (1s, 2s, 4s) with +/- 25% random jitter.
     * Respect `Retry-After` header.
     * Never retry client validation (400), authentication (401), or authorization (403) errors.
     * Bounded by overall `ai.agent.execution-timeout=60s`.
  3. Implement `AiRateLimiterService`:
     * Sliding window rate limiter (20 requests/minute per authenticated user).
     * Global active execution limit (max 10 concurrent runs) to protect HikariCP connection pool.
     * Returns HTTP 429 with `Retry-After` header.
  4. Instrument LangGraph4j nodes and tool executions with Micrometer (`ai.request.duration`, `ai.node.duration`, `ai.tool.duration`, `ai.token.usage`).
  5. Persist every tool execution to `ai_tool_runs` with parameter JSON, execution duration, row counts, and status.
  6. Implement conversation history token budgeting (2,000-token window) to prevent LLM context exhaustion.
* **Deliverables**: Resilience service, rate limiter, audit logging, and Actuator metrics.
* **Complexity**: Medium.

---

### STAGE 5: Testing, Evaluation, and Production Readiness
* **Objective**: Construct a central evaluation dataset with 150+ business scenarios, execute comprehensive regression, security, and load tests across all ERP domains, benchmark provisional SLAs under representative load, and produce the final production readiness evaluation report.
* **Implementation Tasks**:
  1. Build evaluation dataset in `ErpAiDomainEvaluationTest`:
     * Direct questions across all 10 domains.
     * Natural language variations, misspellings, colloquial terms.
     * Relative date interpretation ("last month", "yesterday", "Q3").
     * Multi-condition queries (supplier + date + status).
     * Multi-intent queries (stock + last purchase price).
     * Disambiguation and clarification prompts.
     * Empty result handling.
  2. Implement adversarial tests in `ErpAiSecurityAuthorizationTest`:
     * Cross-user conversation access prevention.
     * Role-based tool access enforcement (Cashier blocked from supplier costs and daily closing).
     * Asynchronous authorization tests across worker thread pools.
     * Prompt injection attacks (jailbreaks, SQL injection strings, secret leakage).
  3. Implement concurrency load tests in `ErpAiConcurrencyLoadTest`.
  4. Benchmark provisional SLAs under representative multi-user concurrent load:
     * Measure separate database/tool latency, LLM provider latency, and end-to-end latency.
     * Verify compliance before claiming production readiness.
  5. Execute full Maven test suite using deterministic mocked LLM responses.
  6. Compile benchmark metrics into `docs/ai-agent/EVALUATION_REPORT.md`.
  7. Finalize production deployment checklist and update `IMPLEMENTATION_STATUS.md`.
* **Deliverables**: Comprehensive test suite, evaluation report, and production sign-off.
* **Complexity**: Medium.

---

## Stage Dependency Matrix

| Stage | Prerequisites | Output Delivered | Verification Milestone |
| :--- | :--- | :--- | :--- |
| **Stage 1** | None | Architecture, matrix, plan, and status docs | Document baseline committed; zero code modifications |
| **Stage 2A** | Approved Stage 1 | Verified Implementation Map & Architecture Safeguard | Audit complete; verified against schema `V1`–`V30`; zero code modifications |
| **Stage 2B** | Approved Stage 2A | Product & Inventory tools, typed state, immutable security context | Unit tests pass; async security authorization verified |
| **Stage 2C** | Approved Stage 2B | Purchase, Sales, Customer, and Supplier tools | Commercial ERP query & role tests pass |
| **Stage 2D** | Approved Stage 2C | Returns, Financial, and Reports tools + audit logging | Operational & financial query tests pass |
| **Stage 2E** | Approved Stage 2D | Multi-domain classifier, context resolver, graph routing | End-to-end regression tests pass; Stage 2 complete |
| **Stage 3** | Approved Stage 2E<br>*(Dependency Note: Flyway reconciliation for V29/V30 must occur before fresh production DB deploy)* | RAG pipeline, pgvector / FTS fallback, citations | Grounded policy queries succeed with citations; transactional isolation verified |
| **Stage 4** | Approved Stage 3 | Transient retry, rate limiter, `ai_tool_runs` audit, metrics | Fault injection tests pass; audit rows verified; metrics exposed |
| **Stage 5** | Approved Stages 1-4 | 150+ evaluation tests, load tests, sign-off report | Test suites pass; provisional SLAs verified under representative load; production ready |
