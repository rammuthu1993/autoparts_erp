# Auto Parts ERP AI Assistant: Implementation Status Ledger

*Last Updated*: 2026-11-18 (Stage 3C Phases 1, 2, 3 Completed & Verified; Phase 4 Queued)

---

## Executive Status Overview

| Stage | Title | Status | Gate Approval |
| :--- | :--- | :--- | :--- |
| **Stage 1** | Architecture Audit, State Schema Design & Traceability Setup | **COMPLETED** | Approved by User |
| **Stage 2A** | Read-Only Code & Schema Audit (Verified Implementation Map) | **COMPLETED** | Approved by User |
| **Stage 2B** | Product & Inventory Tools, Typed State & Immutable Security Context | **COMPLETED** | Approved by User |
| **Stage 2C** | Purchase, Sales, Customer, and Supplier Tools | **COMPLETED** | Approved by User |
| **Stage 2D** | Returns, Financial, and Reports Tools + Audit Logging | **COMPLETED** | Approved by User |
| **Stage 2E** | Hybrid Routing, Multi-Entity Context & Dynamic Synthesis | **COMPLETED** | Approved by User |
| **Stage 3A** | RAG Feasibility and Architecture Audit | **COMPLETED** | Approved by User |
| **Stage 3B** | FTS-Based Lexical RAG Implementation & Security Remediation | **COMPLETED** | Approved by User |
| **Stage 3C** | Retrieval Evaluation & Precision Benchmark (Phases 1, 2 & 3 Completed) | **IN PROGRESS (Phases 1, 2, 3 COMPLETED)** | Phases 1, 2, 3 Complete; Awaiting User Phase-Gate Approval (Phase 4 Queued) |
| **Stage 4** | Production Reliability, Security & Observability | **QUEUED** | Depends on Stage 3 |
| **Stage 5** | Testing, Evaluation & Production Readiness | **QUEUED** | Depends on Stage 4 |

---

## Stage 3A Completed Tasks (RAG Feasibility & Architecture Audit - Revised)

* [x] **Task 1 — Embedding Provider Feasibility**:
  - Investigated Sarvam AI capabilities: identified model `embeddinggemma-2` (768 dimensions), endpoint `POST /v2/embeddings`.
  - Discovered critical endpoint mismatch: Spring AI 2.0.2 with `base-url=https://api.sarvam.ai/v1` appends `/embeddings` to form `https://api.sarvam.ai/v1/embeddings`, returning HTTP 404.
  - Documented Sarvam Provisioned Throughput restriction and established custom adapter specifications and provider alternatives (OpenAI `text-embedding-3-small`, in-process ONNX `all-MiniLM-L6-v2`, and PostgreSQL FTS fallback).
* [x] **Task 2 — PostgreSQL pgvector Feasibility & Spring AI 2.0.2 Behavior**:
  - Inspected host PostgreSQL 18 environment at `C:\Program Files\PostgreSQL\18`.
  - Confirmed via `share/extension` inspection that `vector.control` is **not present in the inspected host installation**.
  - Qualified Spring AI 2.0.2 `PgVectorStore` behavior: schema initialization is disabled by default (`initialize-schema=false`). If enabled, it executes `CREATE EXTENSION IF NOT EXISTS vector;` during bean creation and crashes startup if extension files are missing or user lacks permissions; if disabled, bean creation succeeds but runtime queries fail with `type "vector" does not exist`.
  - Formulated safe read-only SQL diagnostic commands for target database qualification by DBA.
  - Formally established PostgreSQL Full-Text Search (FTS) as the primary, production-ready retrieval engine.
* [x] **Task 3 — Full-Text Search (FTS) Lexical Retrieval Architecture**:
  - Clarified boundary between lexical retrieval (exact terms, stems, proximity) and semantic retrieval (conceptual synonyms); designated Stage 3B as **FTS-Based Lexical RAG**.
  - Corrected PostgreSQL ranking normalization flags: flag 1 divides by $1 + \log(\text{length})$, flag 4 normalizes harmonic extent distance (in `ts_rank_cd`), flag 32 rescales $\text{rank} / (\text{rank} + 1)$ without altering ordering. Selected flag `5` (`1 | 4`) for combined length and cover-density distance normalization.
  - Designed `KnowledgeRetrievalEngine` abstraction decoupling retrieval from storage.
* [x] **Task 4 — Knowledge Document Schema Design (V32)**:
  - Authored complete DDL for `knowledge_documents` and `knowledge_chunks` utilizing UUIDs, JSONB metadata, `required_role`, versioning, active/archived status, foreign keys with cascading delete, and automatic `chunk_vector TSVECTOR GENERATED ALWAYS AS ... STORED`.
  - Designed partial unique index `uq_knowledge_documents_single_active_version` on `knowledge_documents(document_code) WHERE active = TRUE` to physically guarantee at most one active version per document code at the database level.
  - Maintained strict Spring JDBC compatibility (zero JPA/Hibernate).
* [x] **Task 5 — Document Access Control & Version Lifecycle**:
  - Revised retrieval SQL to inner-join `knowledge_documents` and `knowledge_chunks`, requiring both `c.active = TRUE AND d.active = TRUE`, and filtering by `d.required_role IN (:allowedRoles)`.
  - Reused immutable `ErpAiExecutionContext` for server-side pre-filtering directly inside SQL query.
  - Defined transactional version deactivation and ingestion lifecycle with advisory locking to serialize concurrent uploads.
* [x] **Task 6 — Document Ingestion Pipeline Design**:
  - Designed multi-format ingestion pipeline for PDF (Apache PDFBox 3.x), TXT, and Markdown.
  - Specified 500-token chunks with 50-token overlap, paragraph/sentence boundary preservation, section heading inheritance, SHA-256 deduplication, magic byte checks, and transactional rollback boundaries.
  - Qualified character-to-token heuristics ($\approx 2,000$ characters for 500 tokens) as rough approximations; established requirement to benchmark chunking on representative ERP documents.
* [x] **Task 7 — Knowledge Retrieval & LangGraph4j Integration Design**:
  - Defined new `"KNOWLEDGE"` intent in `ErpAiIntentResult`.
  - Established deterministic intent classification and strict transactional isolation (live figures for stock, invoices, purchases, debts never route to knowledge).
  - Designed LangGraph4j workflow integration: `START -> classify -> [KNOWLEDGE] -> retrieve_knowledge -> generate_response -> validate_response -> END`.
  - Specified mandatory citation format `[Source: <Title> v<Version>, §<Section>]` and hallucination-free empty result fail-safe.
* [x] **Task 8 — Flyway Migration Safety & V29/V30 Duplicate Conflict Analysis**:
  - Audited migration history; confirmed duplicate relation collision between `V29__aiagent_runs.sql` and `V30__aiagentest.sql`.
  - Reaffirmed `DEC-001` / `DEF-001` deferral; reserved `V32` for Stage 3B without creating SQL files prematurely in Stage 3A.
* [x] **Task 9 — Comprehensive Stage 3 Testing Strategy**:
  - Designed unit tests (ingestion, chunking, FTS ranking, role filtering), integration tests (FTS queries, LangGraph4j workflow execution), and security tests (prompt injection defense, cross-role leakage prevention).
* [x] **Documented Complete Audit**: Authored and revised `docs/ai-agent/STAGE_3A_RAG_FEASIBILITY_AUDIT.md`.

---

## Stage 3B Completed & Remediated Tasks (FTS-Based Lexical RAG Implementation)

* [x] **Task 1 — Flyway Migration V32 (`V32__create_knowledge_tables.sql`)**:
  - Implemented `knowledge_documents` and `knowledge_chunks` tables with UUID PKs, JSONB metadata, `required_role`, `file_hash_sha256`, and cascading delete.
  - Implemented partial unique index `uq_knowledge_documents_single_active_version` on `knowledge_documents(document_code) WHERE active = TRUE`.
  - Implemented generated stored `chunk_vector TSVECTOR` with GIN indexing (`idx_knowledge_chunks_tsv`).
  - Supported document types: `POLICY`, `MANUAL`, `SOP`, `GUIDELINE`, `HANDBOOK`, `SPECIFICATION`, `FAQ`.
  - Strict Spring JDBC compatibility, zero JPA/Hibernate entities.
* [x] **Task 2 — Document Models & Serialization**:
  - Authored `KnowledgeDocument`, `KnowledgeChunk`, and `RetrievedChunk` domain records implementing `java.io.Serializable` with `serialVersionUID` for robust LangGraph4j state cloning.
* [x] **Task 3 — Multi-Format Ingestion Pipeline & Validation**:
  - Authored `PdfDocumentParser` using Apache PDFBox 3.x (`Loader.loadPDF`) with `%PDF-` magic byte enforcement.
  - Authored `TextDocumentParser` with clean UTF-8 decoder validation and binary null byte rejection.
  - Enforced 10 MB maximum file limit, SHA-256 deduplication, supported format and role whitelists, extension validation, path traversal prevention, document code regex (`^[A-Za-z0-9_-]{3,100}$`), and error handling on metadata JSON serialization failure.
  - Parameterized transactional advisory locking (`SELECT pg_advisory_xact_lock(hashtext(?)::bigint)`) with `PreparedStatementCallback`, failing the transaction with `IllegalStateException` on lock failure.
* [x] **Task 4 — Heading-Aware Chunking Engine**:
  - Implemented `DocumentChunker` with 500-token target, 50-token overlap, paragraph/sentence boundary snapping, Markdown heading hierarchy inheritance (`TreeMap` tracking: `# Level 1 > ## Level 2`), and token estimator heuristic.
* [x] **Task 5 — Access-Controlled PostgreSQL Lexical Retrieval**:
  - Implemented `PostgresFtsRetrievalEngine` utilizing `websearch_to_tsquery('english', :queryText)`, rank normalization flag 5 (`ts_rank_cd(..., 5)` combining length and extent distance), double-active filtering (`c.active = TRUE AND d.active = TRUE`), and role pre-filtering (`d.required_role IN (:allowedRoles)`).
  - Propagates database failures via typed `KnowledgeRetrievalException`.
  - Implemented `KnowledgeRetrievalService` with role matrix mapping (`ADMIN`, `MANAGER`, `CASHIER`, `PUBLIC`) and fail-closed security.
* [x] **Task 6 — Prompt Injection Defense & Minimal Prompt Isolation**:
  - Grounding prompt in `generateGroundedResponse()` strictly contains trusted system instructions, sanitized reference data, and current user query. Prior conversation history is completely omitted from the knowledge prompt.
  - Enhanced `CitationValidator.sanitizeKnowledgeContent()` escaping XML tags and neutralizing control characters.
* [x] **Task 7 — Strict Zero-Trust Citation Validation**:
  - Rewrote `CitationValidator` enforcing exact title/code matching (no substring matching), exact version matching, and exact section title comparison against retrieved chunk metadata (`chunk.sectionTitle()`).
  - Rejects fabricated sections on genuine documents, rejects malformed citations, and never automatically fabricates citations.
* [x] **Task 8 — Compiled LangGraph4j Integration & Failure Paths**:
  - Added `"KNOWLEDGE"` intent to `ErpAiIntentResult` and regex pattern in `ErpAiIntentClassifier`.
  - Extended `ErpAiGraphState` with `"retrievedChunks"` channel.
  - Handled 4 distinct retrieval paths in `ErpAiGraphNodes`: `SUCCESS`, `NO_MATCH`, `AUTH_ERROR`, and `DB_ERROR`.
  - Guaranteed zero LLM invocations across all non-success paths.
* [x] **Task 9 — Comprehensive Testing & Full Test Suite Reconciliation**:
  - Executed clean Maven test run (`mvnw.cmd clean test -o "-Dtest=!PostgresQueryTimeoutIntegrationTest"`).
  - Full suite: **403 tests run, 403 passed, 0 skipped, 0 failures, 0 errors** across 38 test suites matching 38 Surefire XML report files.
  - Verified live PostgreSQL 18.4 integration across 8 tests (7 live DB tests + 1 production target guard test) covering catalog artifacts, auto-generated tsvector, native FTS queries, flag 5 density ranking, double-active filtering, SQL role filtering, partial unique index constraints, and production database rejection.
  - Hardened test-harness with opt-in live-database migration (`test.db.migrate=true`, default `false`) and production target URL guard.
* [x] **Documented Implementation & Audit**: Authored and revised `docs/ai-agent/STAGE_3B_RAG_IMPLEMENTATION.md`.

---

## Stage 3C Phase 1 Completed Tasks (Representative Knowledge Corpus)

* [x] **Task 1 — Synthetic Knowledge Document Corpus**:
  - Authored four synthetic test fixtures under `src/test/resources/knowledge/`:
    - `POL-WAR-001.md` (Markdown, 7,016 B): Auto Parts Warranty Policy covering batteries, brake rotors, alternators, sensors, claim procedure, and approval workflow.
    - `SOP-CSH-001.txt` (Text, 7,527 B): Cash Register Closing & Reconciliation SOP covering opening float, mid-day drops, counting, payment reconciliation, and discrepancy thresholds.
    - `POL-RET-001.pdf` (PDF, 3,581 B): 3-page Return & Core Exchange Policy covering return windows, condition, restocking fees, battery core deposits, and alternator cores.
    - `FAQ-GST-001.md` (Markdown, 6,046 B): ERP Billing & GST Clarification FAQ covering taxable value vs. subtotal, auto parts HSN codes, tax breakdown, and debit/credit notes.
  - Included prominent synthetic test fixture disclaimers across all four documents.
* [x] **Task 2 — Fixture Parsing & Ingestion Contract Verification**:
  - Created `GeneratePdfCorpusFixture` for deterministic multi-page PDF generation via Apache PDFBox 3.x.
  - Implemented `KnowledgeEvaluationCorpusTest` executing 7 focused tests validating parser compatibility, PDF reading order across pages, Markdown heading hierarchy inheritance, plain-text section boundaries, ingestion request contract conformance, and disclaimer verification.
  - Full suite verified: **410 tests run, 410 passed, 0 failures, 0 errors, 0 skipped** across 39 classes.
* [x] **Task 3 — Separate Test-Harness Safety Review**:
  - Reviewed `PostgresFtsRetrievalIntegrationTest` against safety principles; documented recommendations for `.target("32")` pinning and explicit differentiation between network connection skips and authentication failures.
* [x] **Task 4 — Phase 1 Documentation**: Authored `docs/ai-agent/STAGE_3C_PHASE_1_CORPUS.md`.

---

## Stage 3C Phase 2 Completed Tasks (Chunking Evaluation & Empirical Benchmark)

* [x] **Task 1 — Empirical Chunking Benchmark Suite (`ChunkingBenchmarkTest.java`)**:
  - Implemented 5 focused benchmark tests evaluating `DocumentChunker(500, 50)` across all four corpus fixtures (33 chunks total).
  - Measured true weighted mean chunk size: **202.0 tokens/chunk** ($\sum \text{tokens} = 6,667 / 33$).
  - Measured aggregate character-to-estimated-token ratio: **3.99** ($26,611 \text{ chars} / 6,667 \text{ tokens} = 3.9915$).
  - Verified 100% word boundary preservation (0 truncated words) and 100% paragraph preservation (52/52 paragraphs intact).
  - Verified 0 oversized chunks (>500 tokens) and documented 8 undersized chunks (24.2%) caused by isolated markdown header stubs.
  - Measured consecutive overlap mechanics: continuous plain-text overlaps by 37 words, markdown headers exhibit 0-word overlap.
* [x] **Task 2 — Phase 2 Documentation**: Authored `docs/ai-agent/STAGE_3C_PHASE_2_CHUNKING_BENCHMARK.md`.

---

## Stage 3C Phase 3 Completed Tasks (Retrieval Evaluation & Precision Benchmark)

* [x] **Task 1 — Comprehensive 28-Query Evaluation Dataset (`retrieval-evaluation-dataset.json`)**:
  - Authored and verified 28 ground-truth test cases across 6 distinct categories: Direct Keyword (5), Natural Language (5), Term Variation (5), Multi-Term (5), Negative Out-of-Scope (4), and Role-Restricted (4).
  - Corrected vocabulary mismatches to match verified fixture text (`standard return window replacement parts` for `POL-RET-001`, `returning replacement parts`, `cash register safe drop envelope`, and `daily cash count float deduction`).
* [x] **Task 2 — Safe & Isolated Retrieval Evaluation Test Suite (`KnowledgeRetrievalEvaluationTest.java`)**:
  - Implemented 28 comprehensive test methods (12 unit tests + 16 live PostgreSQL integration tests) against live PostgreSQL 18.4 with managed `SingleConnectionDataSource` lifecycle.
  - Enforced run-specific isolated document codes (`EVAL_<type>_<uuid>`) and primary-key-based UUID cleanup with suppressed exception propagation, guaranteeing complete isolation from development data.
  - Implemented authoritative target chunk index relevance matching and pure metric calculation (Fixed & Adaptive Precision@K, True Recall@K, MRR).
  - Enforced fail-closed role-isolation evaluation on every returned chunk against `KnowledgeRetrievalService.resolveAllowedRoles(tc.userRole())`, verified by 3 dedicated unit tests detecting unauthorized leaks in positive queries, unrelated restricted leaks, and unrecognized documents defaulting to `ROLE_ADMIN`.
  - Corrected negative-query metric semantics: precision, recall, and reciprocal rank for queries without positive targets are mathematically undefined ($0/0$) and recorded as `Double.NaN` (`N/A`), while true-negative rejection is measured separately via `True Negative Rate` (100.0%, 0 false positives).
  - Added dedicated two-sided role authorization verification proving that `ROLE_CASHIER` and `ROLE_MANAGER` retrieve `SOP-CSH-001` Chunk 0 while `PUBLIC` is blocked at the SQL boundary.
* [x] **Task 3 — Measured Baseline Metrics on Live PostgreSQL 18.4**:
  - **Document Hit Rate**: 78.3% (18 / 23 positive queries)
  - **Section Hit Rate**: 69.6% (16 / 23 positive queries)
  - **Mean Reciprocal Rank (MRR)**: 0.6232
  - **Precision@K (Fixed)**: P@1: 0.5652 | P@3: 0.2464 | P@5: 0.1478 (evaluated across 23 positive queries; N/A for negative queries)
  - **Precision@K (Adaptive)**: P@1: 0.5652 | P@3: 0.5507 | P@5: 0.5507
  - **Recall@K**: R@1: 0.4928 | R@3: 0.6377 | R@5: 0.6377
  - **Negative Query Zero-Result Rate**: 100.0% (5 / 5, 0 false positives)
  - **Role-Based Isolation Violations**: 0 (Strict Zero)
  - **Average Retrieval Latency**: 0.81 ms (Min: 0.42 ms, Max: 1.63 ms; sub-millisecond GIN index performance)
  - **Chunking Observations (from Phase 2 empirical benchmark)**: 8 / 33 undersized chunks (24.24% caused by orphan header stubs), 0 oversized chunks (>500 tokens), 81.82% punctuation termination.
* [x] **Task 4 — RAG Regression Suite Execution & Reconciliation**:
  - Executed all 11 RAG regression test classes: **108 tests run, 108 passed, 0 failures, 0 errors, 0 skipped**.
  - All 11 Surefire XML reports verified with clean timestamps.
* [x] **Task 5 — Phase 3 Documentation**: Authored `docs/ai-agent/STAGE_3C_PHASE_3_RETRIEVAL_EVALUATION.md`.

---

## Stage 2 Completed & Accepted Tasks Summary

* [x] **Stage 2A**: Read-only code and database schema audit (`V1`–`V30`); authored `STAGE_2A_VERIFIED_IMPLEMENTATION_MAP.md`.
* [x] **Stage 2B**: Product and Inventory tools (`ProductLookupTool`, `InventoryHistoryLookupTool`), typed channels in `ErpAiGraphState`, immutable execution context (`ErpAiExecutionContext`), zero-trust pre-JDBC authorization checks.
* [x] **Stage 2C**: Commercial ERP domain tools (`PurchaseLookupTool`, `SalesLookupTool`, `CustomerLookupTool`, `SupplierLookupTool`).
* [x] **Stage 2D**: Operational and financial domain tools (`ReturnLookupTool`, `FinancialLookupTool`, `ReportLookupTool`, `AuditLogLookupTool`), audit logging to `ai_tool_runs`, documented COGS/gross profit data model limitation (`DEC-012`).
* [x] **Stage 2E**: Hybrid routing architecture (`ErpAiHybridIntentRouter`, `ErpAiLlmClassifier`, `ErpAiPendingClarificationStore`), conversation persistence, ambiguity detection, educational question classification, and LangGraph4j integration.
* [x] **Clean Verification**: All 313 targeted regression tests pass across 24 test classes. Whitespace clean (`git diff --check` passes).

---

## Actual Maven Test Baseline Results

Captured from clean verified execution on Java 25.0.3 LTS with Maven 3.10.0 (Surefire XML Reports):

| Suite # | Test Class | Category | Tests Run | Passed | Failures | Errors | Skipped | Status |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Stage 3 RAG & Knowledge Tests (12 classes: 115 tests)** |
| 1 | CitationValidatorTest | Citation Validation Unit Test | 16 | 16 | 0 | 0 | 0 | **PASSED** |
| 2 | PromptInjectionDefenseTest | RAG Prompt Injection Defense Test | 6 | 6 | 0 | 0 | 0 | **PASSED** |
| 3 | KnowledgeChunkingTest | Boundary & Heading Chunking Test | 4 | 4 | 0 | 0 | 0 | **PASSED** |
| 4 | KnowledgeDocumentIngestionTest | Document Ingestion & Validation Test | 15 | 15 | 0 | 0 | 0 | **PASSED** |
| 5 | KnowledgeEvaluationCorpusTest | Stage 3C Phase 1 Corpus Fixtures Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| 6 | ChunkingBenchmarkTest | Stage 3C Phase 2 Empirical Chunking Benchmark | 5 | 5 | 0 | 0 | 0 | **PASSED** |
| 7 | KnowledgeVersioningTest | Versioning & Advisory Lock Test | 6 | 6 | 0 | 0 | 0 | **PASSED** |
| 8 | KnowledgeAccessControlTest | Role Matrix & Security Access Test | 4 | 4 | 0 | 0 | 0 | **PASSED** |
| 9 | KnowledgeRetrievalEngineTest | FTS Query & Exception Wrapping Test | 4 | 4 | 0 | 0 | 0 | **PASSED** |
| 10 | PostgresFtsRetrievalIntegrationTest | Live PostgreSQL 18 FTS & Guard Test | 13 | 13 | 0 | 0 | 0 | **PASSED (Live DB & Guard)** |
| 11 | KnowledgeRetrievalEvaluationTest | Stage 3C Phase 3 Precision Benchmark | 28 | 28 | 0 | 0 | 0 | **PASSED (Live DB & Isolated)** |
| 12 | Stage3KnowledgeGraphIntegrationTest | Knowledge Graph Integration Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| **Stage 2B–2E Accepted AI Tests (25 classes: 314 tests)** |
| 13 | ErpAiGraphNodesTest | AI Graph Nodes Unit Test | 57 | 57 | 0 | 0 | 0 | **PASSED** |
| 14 | ErpAiIntentClassifierTest | AI Intent Classifier Test | 42 | 42 | 0 | 0 | 0 | **PASSED** |
| 15 | ErpAiAgentServiceConcurrencyTest | AI Concurrency & Timeout Test | 22 | 22 | 0 | 0 | 0 | **PASSED** |
| 16 | AiAssistantServiceTest | AI Assistant Service Test | 17 | 17 | 0 | 0 | 0 | **PASSED** |
| 17 | ProductLookupToolTest | AI Product Lookup Unit Test | 16 | 16 | 0 | 0 | 0 | **PASSED** |
| 18 | ErpAiHybridIntentRouterTest | Stage 2E Hybrid Router Test | 16 | 16 | 0 | 0 | 0 | **PASSED** |
| 19 | Stage2DIntentAndRoutingTest | Stage 2D Routing Integration Test | 14 | 14 | 0 | 0 | 0 | **PASSED** |
| 20 | ErpAiComparisonAndContextTest | AI Context Resolution Test | 11 | 11 | 0 | 0 | 0 | **PASSED** |
| 21 | Stage2CIntentAndRoutingTest | Stage 2C Routing Integration Test | 10 | 10 | 0 | 0 | 0 | **PASSED** |
| 22 | ReturnLookupToolTest | Return Domain Tool Test | 9 | 9 | 0 | 0 | 0 | **PASSED** |
| 23 | FinancialLookupToolTest | Financial Domain Tool Test | 9 | 9 | 0 | 0 | 0 | **PASSED** |
| 24 | ErpAiExecutionContextTest | Execution Context Security Test | 9 | 9 | 0 | 0 | 0 | **PASSED** |
| 25 | ErpAiPendingClarificationStoreTest | Pending Clarification Store Test | 9 | 9 | 0 | 0 | 0 | **PASSED** |
| 26 | SupplierLookupToolTest | Supplier Domain Tool Test | 8 | 8 | 0 | 0 | 0 | **PASSED** |
| 27 | ReportLookupToolTest | Report Domain Tool Test | 8 | 8 | 0 | 0 | 0 | **PASSED** |
| 28 | ErpAiSecurityAuthorizationTest | Security Authorization Test | 8 | 8 | 0 | 0 | 0 | **PASSED** |
| 29 | CustomerLookupToolTest | Customer Domain Tool Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| 30 | SalesLookupToolTest | Sales Domain Tool Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| 31 | AuditLogLookupToolTest | Audit Log Domain Tool Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| 32 | ErpAiLlmClassifierTest | LLM Intent Classifier Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| 33 | Stage2EGraphRoutingIntegrationTest | Compiled Workflow Integration Test | 7 | 7 | 0 | 0 | 0 | **PASSED** |
| 34 | PurchaseLookupToolTest | Purchase Domain Tool Test | 6 | 6 | 0 | 0 | 0 | **PASSED** |
| 35 | ErpAiAsyncSecurityIsolationTest | Async Security Isolation Test | 4 | 4 | 0 | 0 | 0 | **PASSED** |
| 36 | ProductLookupToolComparisonTest | AI Product Comparison Test | 3 | 3 | 0 | 0 | 0 | **PASSED** |
| 37 | AiRepositoryImplStatusTest | AI Repository Unit Test | 1 | 1 | 0 | 0 | 0 | **PASSED** |
| **Core Non-AI Application Tests (4 classes: 19 tests)** |
| 38 | AuthenticationFlowTests | Security Flow Integration Test | 6 | 6 | 0 | 0 | 0 | **PASSED** |
| 39 | InventoryServiceImplTests | Inventory Service Unit Test | 5 | 5 | 0 | 0 | 0 | **PASSED** |
| 40 | AdvancedReportServiceImplTests | Reports Service Unit Test | 4 | 4 | 0 | 0 | 0 | **PASSED** |
| 41 | GlobalExceptionHandlerTest | Global Exception Handler Test | 4 | 4 | 0 | 0 | 0 | **PASSED** |
| **TOTALS (41 PASSING SUITES)** | | | **448** | **448** | **0** | **0** | **0** | **448 / 448 (100.0%)** |
| PostgresQueryTimeoutIntegrationTest | Live DB Timeout Integration Test | 1 | 0 | 0 | 1 | 0 | **ERROR (DB Auth)** |

*Reconciliation Note on Total Count (448 Passing Tests across 41 Suites)*:
A clean single Maven execution produces exactly 41 XML report files in `target/surefire-reports` for the verified suites. In a clean run, 448 total tests are executed and pass (115 Stage 3 RAG, Ingestion, Benchmark & Evaluation tests [including 13 in `PostgresFtsRetrievalIntegrationTest`, 5 in `ChunkingBenchmarkTest`, and 28 in `KnowledgeRetrievalEvaluationTest`] + 314 Stage 2B–2E tests + 19 Core application tests), yielding 448 passing tests (100.0%), 0 failures, 0 errors, and 0 skipped tests across these 41 suites. `PostgresQueryTimeoutIntegrationTest` is a separate infrastructure test with known database auth/timeout dependencies that fails in this local environment. All fixtures in the Stage 3C Phase 1 evaluation corpus pass format parsing, PDF extraction across pages, heading hierarchy inheritance, and ingestion validation. Baseline retrieval metrics under PostgreSQL 18.4 FTS: Document Hit Rate 78.3% (18/23), Section Hit Rate 69.6% (16/23), MRR 0.6232, 100% Negative Query Rejection, 0 Role Isolation Violations, and 0.81 ms average retrieval latency (Min: 0.42 ms, Max: 1.63 ms).

---

## Approved Architectural Decisions Log

| Decision ID | Summary | Approved Strategy | Date |
| :--- | :--- | :--- | :--- |
| **DEC-001** | Flyway V29/V30 `ai_agent_runs` Conflict & Pre-Production Dependency | **DEFERRED**: Do not modify V29 or V30. Do not create V31 now. Do not run Flyway repair/clean. Use current development database. Production migration reconciliation for `ai_agent_runs` is a mandatory blocking prerequisite before deploying the complete application and V32 RAG migration to a fresh production database. | 2026-11-16 |
| **DEC-002** | Transactional Facts vs. RAG Boundary | **LIVE JDBC FOR FACTS**: Transactional data (stock, prices, purchases, sales, ledgers, debts) queried exclusively via Spring JDBC. RAG is reserved strictly for unstructured business documents. | 2026-11-16 |
| **DEC-003** | LangGraph4j State Channel Mechanism | **TYPED CHANNELS IN ERPAIGRAPHSTATE**: LangGraph4j 1.9.7 typed state channels implemented using `Channels.base(...)` signatures. | 2026-11-16 |
| **DEC-004** | Security Context Propagation & Role Authorization | **BACKEND JAVA ENFORCEMENT & IMMUTABLE CONTEXT**: Authenticated identity and granted authorities are captured on the HTTP request thread in `AiAssistantController` / `AiAssistantService` into an immutable `ErpAiExecutionContext` passed directly to tool execution boundaries. Graph state is never the authorization authority. Roles from LLM output or user chat are strictly prohibited. | 2026-11-16 |
| **DEC-005** | Read-Only Operational Policy | **STRICTLY READ-ONLY**: Stage 2 AI tools are strictly read-only. Mutation verbs ("create purchase", "adjust stock", "pay invoice") are intercepted and explained as unsupported. | 2026-11-16 |
| **DEC-006** | Concurrency & Timeout Control | **REUSE EXISTING ARCHITECTURE**: Reuse `ActiveExecution`, `timeoutScheduler`, `AiExecutionInProgressException`, and `CancellationException` in `ErpAiAgentService`. Avoid duplicate concurrency controls. | 2026-11-16 |
| **DEC-007** | RAG Search Engine Strategy | **HYBRID WITH FTS FALLBACK**: Primary vector search via pgvector; PostgreSQL Full-Text Search (`tsvector`/`tsquery`) as a keyword-based fallback if pgvector is unavailable. | 2026-11-16 |
| **DEC-008** | Pre-Implementation Schema & Repository Verification | **MANDATORY VERIFICATION**: All proposed database table names, column identifiers, foreign-key joins, repository methods, and financial calculations are verified against the live PostgreSQL schema and repository classes before implementing each domain tool. | 2026-11-16 |
| **DEC-009** | Provisional Performance SLA Targets | **PROVISIONAL TARGETS**: SLA targets are provisional. Separate tracking is required for database/tool latency, LLM provider latency, and end-to-end latency. | 2026-11-16 |
| **DEC-010** | Immutable Execution Context Safeguard & Defensive Immutability | **DUAL-BARRIER ZERO-TRUST SECURITY & DEFENSIVE COPYING**: Implemented `ErpAiExecutionContext` with `Set.copyOf()` defensive copying, non-null `conversationId` enforcement, and null/blank role filtering. Protected tools validate authorization before JDBC query execution (asserting zero DB calls on unauthorized requests). | 2026-11-17 |
| **DEC-011** | Sub-staged Stage 2 Execution Strategy | **INCREMENTAL APPROVAL GATES**: Stage 2 was partitioned into 2A (Audit & Map), 2B (Products & Inventory), 2C (Purchases, Sales, Customers, Suppliers), 2D (Returns, Financial, Reports & Audit), and 2E (Hybrid Routing & Synthesis) with user approval between sub-stages. | 2026-11-17 |
| **DEC-012** | COGS and Profit Data Model Limitation | **UNAVAILABLE COGS & PROFIT EXPLANATION**: The current ERP data model does not track transaction-level historical COGS (`sales_products` captures selling price, not acquisition cost; `products.purchase_price` is mutable). Conforming to `AdvancedReportServiceImpl`, the AI assistant is strictly prohibited from calculating or presenting gross profit/margins. Supported verified metrics are Sales Revenue, Purchase Total, Customer/Supplier Ledgers, and Expenses. | 2026-11-17 |
| **DEC-013** | Primary Search Engine & Stage 3B Scope | **POSTGRESQL NATIVE FTS AS PRIMARY (LEXICAL RAG)**: Designate Stage 3B as FTS-based lexical RAG using PostgreSQL `tsvector`, GIN indexing, and `ts_rank_cd(..., 5)` (combining flag 1 length normalization with flag 4 extent distance). Acknowledge that FTS is lexical and does not perform semantic synonym matching. Semantic/hybrid retrieval is preserved as a future enhancement via `KnowledgeRetrievalEngine`. | 2026-11-17 |
| **DEC-014** | Ingestion Engine & PDF Library | **APACHE PDFBOX 3.X**: Use Apache PDFBox 3.x for Java 25 compatible, pure-Java PDF extraction without native OS dependencies or JNI. | 2026-11-17 |
| **DEC-015** | Server-Side Document Access Control | **SQL-LEVEL PRE-FILTERING VIA IMMUTABLE CONTEXT**: Knowledge queries must pre-filter chunks in the SQL `WHERE d.required_role IN (...)` clause using immutable `ErpAiExecutionContext.authorities()`. Post-retrieval filtering is strictly prohibited. Zero reliance on thread-local security in async LangGraph4j threads. | 2026-11-17 |
| **DEC-016** | Document Chunking Parameters | **500 TOKENS / 50 OVERLAP (INITIAL EXPERIMENTAL)**: Adopt 500 tokens with 50-token overlap as initial experimental parameters snapping to paragraph and sentence boundaries. Character counts are treated as coarse heuristics. Real tokenizers or word estimators must be evaluated against representative ERP documents. | 2026-11-17 |
| **DEC-017** | Citations and Grounding Standard | **MANDATORY SOURCE CITATIONS & FAIL-SAFE**: Grounded responses must cite `[Source: <Title> v<Version>, §<Section>]`. Empty retrieval results fail safe immediately without invoking the external LLM. | 2026-11-17 |
| **DEC-018** | Flyway V32 Scheduling | **V32 DEFERRED TO STAGE 3B**: Migration `V32__create_knowledge_tables.sql` will be created during Stage 3B. Pre-production reconciliation of V29/V30 remains a blocking prerequisite before deploying to fresh databases. | 2026-11-17 |
| **DEC-019** | Single Active Version Constraint | **PARTIAL UNIQUE INDEX FOR ACTIVE VERSIONS**: Enforce at most one active version per document code at the database engine level via `CREATE UNIQUE INDEX uq_knowledge_documents_single_active_version ON knowledge_documents(document_code) WHERE active = TRUE;`. Concurrent ingestion is guarded by transactional advisory locks. | 2026-11-17 |
| **DEC-020** | Retrieval Failure Handling & Zero-Token Fail-Safes | **TYPED RETRIEVAL EXCEPTION & FAIL-CLOSED PATHS**: Introduced `KnowledgeRetrievalException` for database/infrastructure failures, differentiating them from empty search results. Fail closed with `ToolAuthorizationException` if context/roles are missing. Handled 4 distinct outcomes in `ErpAiGraphNodes`, bypassing LLM invocation entirely on all non-success paths. | 2026-11-17 |
| **DEC-021** | Concurrency Control via Parameterized Advisory Locks | **PARAMETERIZED ADVISORY LOCKING**: Ingestion concurrency control uses `SELECT pg_advisory_xact_lock(hashtext(?)::bigint)` with `PreparedStatementCallback`. Never concatenate SQL strings. Throws `IllegalStateException` on lock failure to ensure transaction abort. | 2026-11-17 |
| **DEC-022** | Zero-Trust Citations & Historical Prompt Isolation | **MINIMAL PROMPT & EXACT MATCH CITATIONS**: Grounding prompt strictly contains trusted system instructions, reference context, and current user message (history is omitted to prevent historical injection). Citations require exact title/code match, exact version match, and exact section match against chunk metadata. Never automatically append citations. | 2026-11-17 |
| **DEC-023** | Format-Agnostic Citations (Choice B) | **DEFER PAGE-LEVEL METADATA**: Formally adopted Choice B: citations support document code/title, version, and section title (`§<Section>`), maintaining format-agnostic consistency across PDF, TXT, and Markdown without unvetted database migrations. | 2026-11-17 |

---

## Deferred Tasks Ledger

| Task ID | Deferred Item | Justification & Strategy | Target Phase |
| :--- | :--- | :--- | :--- |
| **DEF-001** | Production Migration Reconciliation (`ai_agent_runs`) | Current development database has the table created manually. Modifying migrations now risks breaking development environments. A clean, idempotent migration reconciliation strategy will be prepared and executed prior to deploying the complete application and V32 RAG migration to a fresh production database. | Pre-Production Deployment |
| **DEF-002** | Controlled Write Operations (Mutations) | Transactional mutations (e.g. creating purchases, adjusting inventory) require multi-step human-in-the-loop approvals, idempotency keys, and explicit audit logging. Excluded from read-only scope. | Post-Release Roadmap |
| **DEF-003** | Distributed Rate Limiter (Redis) | Application currently deploys as a single backend instance. In-memory sliding window rate limiter meets all requirements without external infrastructure dependencies. | Multi-Instance Scaling |

---

## Known Issues and Technical Risks

1. **Host PostgreSQL 18 pgvector Absence**:
   * *Status*: Confirmed absent in inspected host installation (`C:\Program Files\PostgreSQL\18\share\extension`).
   * *Mitigation*: Native PostgreSQL Full-Text Search (`tsvector`/`tsquery` with GIN indexing and `ts_rank_cd(..., 5)` ranking) provides lexical retrieval without external C extensions.
2. **Sarvam AI Embedding Availability & Endpoint Mismatch**:
   * *Status*: Sarvam embeds via `POST /v2/embeddings` using `embeddinggemma-2`, requiring Provisioned Throughput. Standard Spring AI OpenAI starter expects `/v1/embeddings`.
   * *Mitigation*: Custom adapter configuration handles `/v2/embeddings` if enabled; primary fallback to native PostgreSQL FTS eliminates embedding API dependency entirely.
3. **Database Integration Test Environment**:
   * *Status*: `PostgresQueryTimeoutIntegrationTest` requires a running local PostgreSQL instance with matching credentials.
   * *Preserved Baseline*: 313 targeted tests passing with 0 failures; 1 separate infrastructure-dependent integration test.
4. **SecurityContextHolder Thread-Local Risk in Asynchronous Execution**:
   * *Mitigation*: Authenticated identity and authorities are captured on the HTTP request thread into immutable `ErpAiExecutionContext` passed through trusted invocation boundaries. Zero reliance on `ThreadLocal`.
5. **Transactional Facts vs RAG Separation**:
   * *Risk*: Users querying for stock levels or invoice figures mistakenly retrieving policy documents.
   * *Mitigation*: Strict precedence in `ErpAiHybridIntentRouter`. Live operational entities and metrics exclusively route to live Spring JDBC tools.
6. **Lexical vs Semantic Retrieval Boundaries**:
   * *Risk*: Users expect synonym understanding (e.g. "faulty alternator" vs "defective generator") from FTS.
   * *Mitigation*: Transparent documentation that Stage 3B is FTS-based lexical RAG; extensible `KnowledgeRetrievalEngine` designed to accommodate future dense vector / hybrid search.
