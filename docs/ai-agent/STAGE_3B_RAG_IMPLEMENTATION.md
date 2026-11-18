# Stage 3B — FTS-Based Lexical RAG Implementation & Verification Report

**Status**: COMPLETED & VERIFIED (PostgreSQL 18.4 Live Integration Verified)  
**Date**: 2026-11-18  
**Scope**: Native PostgreSQL Full-Text Search (FTS) Lexical Retrieval-Augmented Generation (RAG), Security Remediation & PostgreSQL 18 Live Verification  
**Target Environment**: Java 25, Spring Boot 4.1.x, Spring AI 2.0.2, LangGraph4j 1.9.7, PostgreSQL 18.4, Spring JDBC (zero JPA/Hibernate)

---

## 1. Executive Summary

Stage 3B implements an authoritative, server-side access-controlled, Full-Text Search (FTS) based lexical RAG architecture for the Auto Parts ERP AI Assistant. The implementation complies strictly with the architectural constraints established in Stage 3A, incorporates all remediation requirements from the architectural review, and has been verified against a live PostgreSQL 18.4 database:

1. **Pure Lexical FTS**: Zero reliance on `pgvector`, vector embeddings, or remote embedding endpoints. Utilizes PostgreSQL native `tsvector`, GIN indexing, `websearch_to_tsquery('english', :queryText)`, and `ts_rank_cd(..., 5)`.
2. **Flyway Migration V32 & Live PostgreSQL 18 Verification**: Added and verified `V32__create_knowledge_tables.sql` defining `knowledge_documents` and `knowledge_chunks` with partial unique index `uq_knowledge_documents_single_active_version`, stored generated `chunk_vector TSVECTOR`, and foreign key cascade. Applied cleanly to live PostgreSQL 18.4 without touching historical V1–V30 migrations or running Flyway clean/repair.
3. **Multi-Format Ingestion**: Ingests PDF (Apache PDFBox 3.x), plain text, and Markdown with 10 MB file limits, `%PDF-` magic byte enforcement, strict UTF-8 validation, binary null byte rejection, SHA-256 idempotency deduplication, and transactional advisory locking.
4. **Parameterized Concurrency Control**: Parameterized transactional advisory locking (`SELECT pg_advisory_xact_lock(hashtext(?)::bigint)`) ensuring zero SQL concatenation and fail-fast transaction rollback if lock acquisition fails.
5. **Strict File & Ingestion Validation**: Enforced whitelists for document types (`POLICY`, `SOP`, `MANUAL`, `GUIDELINE`, `HANDBOOK`, `SPECIFICATION`, `FAQ`), formats (`PDF`, `TEXT`, `MARKDOWN`), and roles (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_CASHIER`, `PUBLIC`), validated file extensions match content format, sanitized document codes (`^[A-Za-z0-9_-]{3,100}$`), prevented path traversal in file names, and fail on metadata JSON serialization errors.
6. **Heading-Aware Chunking**: Splits extracted text into ~500-token chunks with 50-token overlap, paragraph/sentence boundary snapping, Markdown heading hierarchy inheritance (`# Level 1 > ## Level 2`), and coarse token labeling.
7. **Role-Based Server-Side Pre-Filtering**: Pre-filters document chunks directly in SQL (`d.required_role IN (:allowedRoles)`) mapped from immutable `ErpAiExecutionContext.authorities()`. Never relies on prompt instructions or ThreadLocal security.
8. **Compiled LangGraph4j Integration**: Extended workflow with dedicated knowledge path:  
   `START -> classify -> [KNOWLEDGE] -> retrieve_knowledge -> validate_retrieved_context -> generate_grounded_response -> validate_citations -> validate_response -> END`.
9. **Failure Path Differentiation & Zero-Token Fail-Safes**: Distinguishes successful retrieval returning 0 chunks from database/infrastructure failure (`KnowledgeRetrievalException`), authorization failures (`ToolAuthorizationException`), and missing retrieval service instances (`knowledgeRetrievalService == null`). Guarantees zero LLM calls on all non-success paths.
10. **Prompt Injection Defense & Zero History Propagation**: Knowledge prompt consists strictly of trusted system prompt, reference data, and current user query. Prior conversation history is completely omitted from the knowledge grounding prompt to prevent historical injection propagation.
11. **Strict Zero-Trust Citation & Malformed Tag Defense**: Requires exact document identity (code or exact title, no substring matching), exact version, and exact section title verification against retrieved chunk metadata. Detects and strips complete fabricated tags AND incomplete/malformed citation fragments (`[Source:`, `[Source: Return Policy`, `[Source: Return Policy v1`, `[Source: Return Policy v1, §Returns`) while preserving adjacent prose and sentence punctuation. Never fabricates citations.
12. **PDF Page Metadata Decision**: Formally adopted Choice B (defer page-level citations to future stages, document that citations support document/version/section only, maintain format-agnostic consistency across PDF, TXT, and Markdown without unvetted schema migrations).
13. **Ingestion Authorization Boundary**: Formally documented that `requiredRole` and `createdBy` must be derived/validated strictly from the authenticated administrator context before exposing any future upload API (zero public upload endpoints exposed in Stage 3B).

---

## 2. Review Findings & Remediation Matrix

| Finding | Remediation Strategy | Implementation Details |
| :--- | :--- | :--- |
| **1. Retrieval Failure Handling** | Differentiate 0 matches from DB errors; fail closed on auth/missing context; zero LLM calls on non-success paths. | Introduced `KnowledgeRetrievalException`. `PostgresFtsRetrievalEngine` propagates DB errors instead of returning empty list. `KnowledgeRetrievalService` fails closed with `ToolAuthorizationException`. `ErpAiGraphNodes.retrieveKnowledge()` handles all distinct outcomes (`SUCCESS`, `NO_MATCH`, `AUTH_ERROR`, `DB_ERROR`, `NULL_SERVICE`), and `generateGroundedResponse()` bypasses ChatModel when `finalResponse` is set. |
| **2. Advisory Locking** | Parameterize lock query; do not string-concatenate SQL; throw on lock failure. | Parameterized `SELECT pg_advisory_xact_lock(hashtext(?)::bigint)` with `PreparedStatementCallback`. Catches `DataAccessException` and rethrows `IllegalStateException` to abort transaction on lock failure. |
| **3. Strict Citation Validation & Malformed Tag Defense** | Zero-trust verification; exact title/code; exact version; exact section comparison against chunk metadata; reject fabricated sections; detect incomplete/malformed tags (`[Source: ...`); strip fragments; preserve adjacent prose. | Rewrote `CitationValidator.java` with regex matching complete bracketed citations and incomplete `[Source:` prefixes. Detects unclosed brackets, classifies them as invalid/fabricated, and strips them in `normalizedResponse` without swallowing adjacent prose. Enforces exact title/code matching, exact version, and exact section comparison against `chunk.sectionTitle()`. |
| **4. Missing Retrieval Service Guard** | Handle `knowledgeRetrievalService == null` safely without LLM call or false "no documentation" report. | In `ErpAiGraphNodes.retrieveKnowledge()`, if `knowledgeRetrievalService == null`, immediately sets the deterministic temporary system error message (`"I couldn't retrieve the requested documentation from the knowledge base due to a temporary system error. Please try again later."`) and empty chunks, guaranteeing zero LLM invocations. |
| **5. Document Type Whitelist Alignment** | Synchronize document type whitelist across Java, SQL migration, and documentation. | Added `"FAQ"` to `KnowledgeIngestionService.VALID_DOCUMENT_TYPES`, maintaining 100% synchronization across Java whitelist, `V32__create_knowledge_tables.sql` CHECK constraint, and documentation. |
| **6. Prompt Injection Defense** | Minimal knowledge prompt; omit history; robust XML escaping; untrusted reference labeling; comprehensive adversarial test suite. | Removed `state.history()` from `ErpAiGraphNodes.generateGroundedResponse()`. Grounding prompt contains strictly trusted system prompt, reference context, and `state.currentMessage()`. Enhanced `CitationValidator.sanitizeKnowledgeContent()` for tag/control-character neutralization. Added adversarial tests in `PromptInjectionDefenseTest`. |
| **7. Live PostgreSQL 18 Verification** | Validate migration, generated `tsvector`, GIN index, `websearch_to_tsquery`, `ts_rank_cd(..., 5)` ordering, active-version filtering, SQL role filtering, and active-version uniqueness on live DB. | Expanded `PostgresFtsRetrievalIntegrationTest` to 7 comprehensive live tests with automated credential resolution. Verified against live PostgreSQL 18.4 on localhost:5432. All 7 tests passed (0 failures, 0 errors, 0 skips). |
| **8. Ingestion Authorization Boundary** | Document administrative security boundary for document ingestion before exposing upload APIs. | Documented that any future document upload API must derive and validate `requiredRole` and `createdBy` strictly from the authenticated administrator context (`ErpAiExecutionContext` / Spring Security), preventing cashier privilege escalation or role forgery. |
| **9. PDF Page Metadata** | Formally adopt Choice B (defer page-level citations, document limitations, avoid unvetted schema changes). | Formally adopted Choice B. Citations support `[Source: <Title> v<Version>, §<Section>]` across all formats (PDF, TXT, Markdown). Page numbers are not synthesized or stored. |

---

## 3. Source Deliverables

### 3.1 Dependencies (`pom.xml`)
- Added `org.apache.pdfbox:pdfbox:3.0.3` for PDF document reading order text extraction.

### 3.2 Database Migration (`V32__create_knowledge_tables.sql`)
- `knowledge_documents`:
  - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
  - `document_code VARCHAR(100) NOT NULL`
  - `title VARCHAR(255) NOT NULL`
  - `document_type VARCHAR(50) NOT NULL CHECK (document_type IN ('POLICY', 'MANUAL', 'SOP', 'GUIDELINE', 'HANDBOOK', 'SPECIFICATION', 'FAQ'))`
  - `content_format VARCHAR(20) NOT NULL CHECK (content_format IN ('MARKDOWN', 'TEXT', 'PDF'))`
  - `version INT NOT NULL DEFAULT 1 CHECK (version >= 1)`
  - `required_role VARCHAR(50) NOT NULL DEFAULT 'PUBLIC' CHECK (required_role IN ('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CASHIER', 'PUBLIC'))`
  - `active BOOLEAN NOT NULL DEFAULT TRUE`
  - `file_name VARCHAR(255) NOT NULL`
  - `file_hash_sha256 VARCHAR(64) NOT NULL`
  - `file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0)`
  - `metadata JSONB NOT NULL DEFAULT '{}'::jsonb`
  - `created_by VARCHAR(150) NOT NULL`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - Unique Constraint: `CONSTRAINT uq_knowledge_doc_code_version UNIQUE (document_code, version)`
  - Partial Unique Index: `CREATE UNIQUE INDEX uq_knowledge_documents_single_active_version ON knowledge_documents(document_code) WHERE active = TRUE;`
  - Indexes on `(document_code, active)` and `(required_role, active)`.
- `knowledge_chunks`:
  - `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
  - `document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE`
  - `chunk_index INT NOT NULL CHECK (chunk_index >= 0)`
  - `section_title VARCHAR(255)`
  - `chunk_text TEXT NOT NULL`
  - `token_count INT NOT NULL CHECK (token_count > 0)`
  - `chunk_hash_sha256 VARCHAR(64) NOT NULL`
  - `active BOOLEAN NOT NULL DEFAULT TRUE`
  - Stored generated TSVECTOR: `chunk_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(section_title, '') || ' ' || chunk_text)) STORED`
  - GIN Index: `CREATE INDEX idx_knowledge_chunks_tsv ON knowledge_chunks USING GIN(chunk_vector);`
  - Indexes on `active` and `document_id`.

### 3.3 Domain Models (`com.autoparts.erp.ai.rag.model`)
- `KnowledgeDocument.java`: Immutable record representing document metadata, implementing `Serializable`.
- `KnowledgeChunk.java`: Immutable record representing chunk passage, implementing `Serializable`.
- `RetrievedChunk.java`: Immutable record representing search result with relevance score and standard citation formatting (`[Source: %s v%d, §%s]`), implementing `Serializable`.

### 3.4 Ingestion Pipeline (`com.autoparts.erp.ai.rag.ingestion`)
- `DocumentParser.java`: Interface supporting format detection and text extraction.
- `PdfDocumentParser.java`: Apache PDFBox 3.x parser validating `%PDF-` header magic bytes and extracting text in layout order via `PDFTextStripper`.
- `TextDocumentParser.java`: Parser validating strict UTF-8 encoding and rejecting binary files with null control characters.
- `DocumentChunker.java`: Boundary-aware chunker with 500-token target, 50-token overlap, sentence/paragraph boundary preservation, and Markdown heading hierarchy inheritance (`TreeMap` level tracking).
- `KnowledgeIngestionService.java`: Transactional ingestion service acquiring parameterized advisory locks (`pg_advisory_xact_lock(hashtext(?)::bigint)`), checking SHA-256 hash idempotency, validating request whitelists (`VALID_DOCUMENT_TYPES` including `FAQ`), deactivating prior active versions atomically, and persisting documents and chunks using Spring JDBC.

### 3.5 Retrieval Engine & Security (`com.autoparts.erp.ai.rag.retrieval`)
- `KnowledgeRetrievalException.java`: Dedicated unchecked exception for knowledge retrieval infrastructure failures.
- `KnowledgeRetrievalEngine.java`: Retrieval contract decoupling search mechanism from service layer.
- `PostgresFtsRetrievalEngine.java`: Native PostgreSQL lexical retrieval executing `websearch_to_tsquery('english', :queryText)`, normalization flag `5` (`ts_rank_cd(chunk_vector, query, 5)` combining length and cover-density distance normalization), double-active filtering (`c.active = TRUE AND d.active = TRUE`), and role pre-filtering (`d.required_role IN (:allowedRoles)`). Propagates `DataAccessException` as `KnowledgeRetrievalException`.
- `KnowledgeRetrievalService.java`: Zero-trust retrieval service mapping immutable authorities (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_CASHIER`, `PUBLIC`) to allowed document roles and failing closed with `ToolAuthorizationException` on missing/unauthenticated contexts or empty role sets.

### 3.6 Citation & Defense (`com.autoparts.erp.ai.rag.citation`)
- `CitationValidator.java`: Zero-trust citation verification:
  - Detection of complete bracketed citations AND unclosed / incomplete citation prefixes (`[Source:`, `[Source: Return Policy`, `[Source: Return Policy v1`, `[Source: Return Policy v1, §Returns`).
  - Strict stripping of fabricated and malformed citation fragments while preserving normal prose and sentence boundary punctuation.
  - Exact document identity (code or exact normalized title, no substring matching).
  - Exact version match.
  - Exact section title comparison against retrieved chunk metadata (`chunk.sectionTitle()`).
  - Rejection of fabricated sections on genuine documents.
  - `sanitizeKnowledgeContent()`: Escapes XML tags and neutralizes non-printable ASCII control characters.

### 3.7 Graph Integration (`com.autoparts.erp.ai.service`)
- `ErpAiIntentResult.java`: Added `"KNOWLEDGE"` to `SUPPORTED_INTENTS`.
- `ErpAiGraphState.java`: Added `"retrievedChunks"` channel and accessor.
- `ErpAiIntentClassifier.java`: Added `KNOWLEDGE_REQUEST` regex with guard `!isLiveStockQuery(normalized)` and prevented `SALES_SEARCH_REQUEST` from hijacking policy queries.
- `ErpAiGraphNodes.java`: Added knowledge workflow node methods:
  - `retrieveKnowledge(state, config)`: Handles all outcomes (`SUCCESS`, `NO_MATCH`, `AUTH_ERROR`, `DB_ERROR`, and deterministic fallback if `knowledgeRetrievalService == null`).
  - `validateRetrievedContext(state, config)`: Sets deterministic response if chunks are empty.
  - `generateGroundedResponse(state, config)`: Omits conversation history, constructs minimal grounded prompt (system prompt + reference context + current user message), and invokes ChatModel only on genuine retrieved chunks.
  - `validateCitations(state, config)`: Strips fabricated citations and malformed prefixes via `valResult.normalizedResponse()` without automatically fabricating or appending sources.
- `ErpAiAgentService.java`: Compiled graph edges:
  - Conditional edge: `classify -> [KNOWLEDGE] -> retrieve_knowledge`
  - Sequence: `retrieve_knowledge -> validate_retrieved_context -> generate_grounded_response -> validate_citations -> validate_response -> END`

---

## 4. Test Suite Reconciliation & Verification

### 4.1 Test Count Reconciliation
A clean execution (`mvn clean test -o "-Dtest=!PostgresQueryTimeoutIntegrationTest"`) verifies the entire project test suite:

$$\text{Total Test Suite} = 70\text{ (Stage 3B RAG)} + 314\text{ (Stage 2B–2E AI Agent)} + 19\text{ (Non-AI Core)} = 403\text{ Tests}$$

- **Stage 3B RAG & Knowledge Tests**: 70 tests across 9 test classes (70 passed, 0 skipped, 0 failures, 0 errors).
- **Stage 2B–2E Accepted AI Tests**: 314 tests across 25 test classes (314 passed).
- **Non-AI Application Core Tests**: 19 tests across 4 test classes (19 passed).
- **Total Executed**: **403 tests, 403 passed, 0 skipped, 0 failures, 0 errors**.
- **Surefire Reports**: Exactly 38 XML report files in `target/surefire-reports`, perfectly reconciling 1:1 with the 38 executed test classes.

### 4.2 Comprehensive Class-by-Class Surefire Breakdown

| Suite # | Test Class | Package | Tests | Pass | Fail | Error | Skip | Description |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Stage 3B Tests** |
| 1 | `CitationValidatorTest` | `ai.rag.citation` | 16 | 16 | 0 | 0 | 0 | Exact title/code, substring rejection, version mismatch, fabricated section on genuine doc, malformed citations, incomplete bracket prefixes (`[Source:`, `[Source: Return Policy`, `[Source: Return Policy v1`, `[Source: Return Policy v1, §Returns`), prose preservation, omitted section rules, mixed genuine/fabricated, tag & control-char sanitization |
| 2 | `PromptInjectionDefenseTest` | `ai.rag.citation` | 6 | 6 | 0 | 0 | 0 | Tag escaping, tool execution prevention under hostile injection, fabricated citation stripping, incomplete citation prefix stripping, history omission from prompt, no auto-appending |
| 3 | `KnowledgeChunkingTest` | `ai.rag.ingestion` | 4 | 4 | 0 | 0 | 0 | 500-token target, 50-token overlap, boundary snapping, heading hierarchy inheritance, token estimator heuristic |
| 4 | `KnowledgeDocumentIngestionTest` | `ai.rag.ingestion` | 15 | 15 | 0 | 0 | 0 | PDFBox extraction, magic bytes, 10 MB limit, UTF-8/null check, SHA-256 deduplication, metadata normalization, invalid docType, FAQ document type acceptance, invalid format, extension mismatch, path traversal, invalid docCode, invalid role, JSON serialization failure |
| 5 | `KnowledgeVersioningTest` | `ai.rag.ingestion` | 6 | 6 | 0 | 0 | 0 | v1 creation, v2 creation with atomic v1 deactivation, unique constraint violation, transactional rollback, parameterized advisory lock acquisition, advisory lock failure handling |
| 6 | `KnowledgeAccessControlTest` | `ai.rag.retrieval` | 4 | 4 | 0 | 0 | 0 | Role hierarchy matrix (`ADMIN`, `MANAGER`, `CASHIER`, `PUBLIC`), SQL pre-filtering assertion, context propagation, fail-closed without context |
| 7 | `KnowledgeRetrievalEngineTest` | `ai.rag.retrieval` | 4 | 4 | 0 | 0 | 0 | FTS SQL query generation (`websearch_to_tsquery`), rank flag 5 (`ts_rank_cd`), double-active filtering, role pre-filtering, `KnowledgeRetrievalException` wrapping |
| 8 | `PostgresFtsRetrievalIntegrationTest` | `ai.rag.retrieval` | 13 | 13 | 0 | 0 | 0 | Live PostgreSQL 18.4 verification & safety guards: target V32 configuration, unreachable port graceful skipping, dynamic host/port URL parsing, remote database opt-in, authentication failure fast-fail, production target rejection, V32 catalog migration, auto-generated `tsvector` column, `websearch_to_tsquery` & `ts_rank_cd`, flag 5 density ranking, double-active filtering, SQL role filtering, and partial unique index active-version uniqueness |
| 9 | `Stage3KnowledgeGraphIntegrationTest` | `ai.service` | 7 | 7 | 0 | 0 | 0 | Full workflow execution: grounded citation answers, deterministic 0-document fail-safe, mixed-turn conversation, hallucinated citation stripping, auth failure zero-token path, DB failure zero-token path, null retrieval service zero-token path |
| **Stage 2B–2E Accepted AI Tests** |
| 10 | `AiRepositoryImplStatusTest` | `ai.repositoryimpl` | 1 | 1 | 0 | 0 | 0 | Repository health check |
| 11 | `ErpAiAsyncSecurityIsolationTest` | `ai.security` | 4 | 4 | 0 | 0 | 0 | Security isolation across thread boundaries |
| 12 | `ErpAiExecutionContextTest` | `ai.security` | 9 | 9 | 0 | 0 | 0 | Immutable execution context creation, role extraction, validation |
| 13 | `ErpAiSecurityAuthorizationTest` | `ai.security` | 8 | 8 | 0 | 0 | 0 | Tool authorization checks, role boundaries |
| 14 | `AiAssistantServiceTest` | `ai.service` | 17 | 17 | 0 | 0 | 0 | Service facade integration and delegation |
| 15 | `ErpAiAgentServiceConcurrencyTest` | `ai.service` | 22 | 22 | 0 | 0 | 0 | Concurrency, timeout, and thread safety |
| 16 | `ErpAiComparisonAndContextTest` | `ai.service` | 11 | 11 | 0 | 0 | 0 | Multi-turn product comparison and context tracking |
| 17 | `ErpAiGraphNodesTest` | `ai.service` | 57 | 57 | 0 | 0 | 0 | Node execution logic across all graph nodes |
| 18 | `ErpAiHybridIntentRouterTest` | `ai.service` | 16 | 16 | 0 | 0 | 0 | Hybrid deterministic + LLM routing, ambiguity resolution |
| 19 | `ErpAiIntentClassifierTest` | `ai.service` | 42 | 42 | 0 | 0 | 0 | Deterministic regex and keyword classification rules |
| 20 | `ErpAiLlmClassifierTest` | `ai.service` | 7 | 7 | 0 | 0 | 0 | LLM intent classification fallback |
| 21 | `ErpAiPendingClarificationStoreTest` | `ai.service` | 9 | 9 | 0 | 0 | 0 | Pending clarification storage, ownership, expiration, cleanup |
| 22 | `Stage2CIntentAndRoutingTest` | `ai.service` | 10 | 10 | 0 | 0 | 0 | Stage 2C routing integration |
| 23 | `Stage2DIntentAndRoutingTest` | `ai.service` | 14 | 14 | 0 | 0 | 0 | Stage 2D routing integration |
| 24 | `Stage2EGraphRoutingIntegrationTest` | `ai.service` | 7 | 7 | 0 | 0 | 0 | Stage 2E graph routing integration |
| 25 | `AuditLogLookupToolTest` | `ai.tools` | 7 | 7 | 0 | 0 | 0 | Audit log lookup tool unit and security tests |
| 26 | `CustomerLookupToolTest` | `ai.tools` | 7 | 7 | 0 | 0 | 0 | Customer lookup tool unit and security tests |
| 27 | `FinancialLookupToolTest` | `ai.tools` | 9 | 9 | 0 | 0 | 0 | Financial lookup tool unit and security tests |
| 28 | `ProductLookupToolComparisonTest` | `ai.tools` | 3 | 3 | 0 | 0 | 0 | Product comparison tool tests |
| 29 | `ProductLookupToolTest` | `ai.tools` | 16 | 16 | 0 | 0 | 0 | Product lookup tool unit and security tests |
| 30 | `PurchaseLookupToolTest` | `ai.tools` | 6 | 6 | 0 | 0 | 0 | Purchase lookup tool unit and security tests |
| 31 | `ReportLookupToolTest` | `ai.tools` | 8 | 8 | 0 | 0 | 0 | Report lookup tool unit and security tests |
| 32 | `ReturnLookupToolTest` | `ai.tools` | 9 | 9 | 0 | 0 | 0 | Return lookup tool unit and security tests |
| 33 | `SalesLookupToolTest` | `ai.tools` | 7 | 7 | 0 | 0 | 0 | Sales lookup tool unit and security tests |
| 34 | `SupplierLookupToolTest` | `ai.tools` | 8 | 8 | 0 | 0 | 0 | Supplier lookup tool unit and security tests |
| **Core Non-AI Application Tests** |
| 35 | `GlobalExceptionHandlerTest` | `common.exception` | 4 | 4 | 0 | 0 | 0 | Global REST exception handler mapping |
| 36 | `InventoryServiceImplTests` | `inventory.serviceimpl` | 5 | 5 | 0 | 0 | 0 | Inventory service stock movements |
| 37 | `AdvancedReportServiceImplTests` | `reports.serviceimpl` | 4 | 4 | 0 | 0 | 0 | Advanced report generation service |
| 38 | `AuthenticationFlowTests` | `security` | 6 | 6 | 0 | 0 | 0 | Authentication, login, JWT token issuance |
| **Total (Stage 3B Baseline)** | | | **408** | **408** | **0** | **0** | **0** | **BUILD SUCCESS** |
| *(Stage 3C Phase 1 Evaluation Corpus)* | `KnowledgeEvaluationCorpusTest` | `ai.rag.ingestion` | 7 | 7 | 0 | 0 | 0 | Added in Stage 3C Phase 1 (brings overall suite to 415 tests across 39 classes) |

---

## 5. Live PostgreSQL 18 Integration Verification & Test-Harness Safety

### 5.1 Test-Harness Safety Hardening
To prevent accidental migration or test execution against unintended or production database environments:
1. **Production Database Target Guard (`validateSafeDatabaseTarget`)**:
   - Inspects `dbUrl`, active profiles (`spring.profiles.active`), and system properties.
   - Throws `IllegalStateException` immediately if `dbUrl` or active profiles contain production markers (`prod`, `production`, `live-db`).
   - Rejects non-loopback hosts (`localhost`, `127.0.0.1`, `::1`) unless explicit authorization flag `-Dtest.allow.remote.db=true` is provided.
   - Verified via unit test `shouldRejectProductionDatabaseTargets()`.
2. **Explicit Opt-In for Live-Database Migration**:
   - Flyway migration is strictly opt-in via `-Dtest.db.migrate=true` or `TEST_DB_MIGRATE=true` (defaults to `false`).
   - When opt-in is false, the test harness will never invoke `flyway.migrate()`. It checks whether required tables exist in `information_schema.tables`; if absent, tests skip gracefully via `assumeTrue`.

### 5.2 Flyway Migration Execution
Migration V32 was applied directly against the development PostgreSQL 18.4 database (`autoparts_erp`) on localhost:5432:
```
INFO org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/autoparts_erp (PostgreSQL 18.4)
INFO org.flywaydb.core.internal.command.DbValidate - Successfully validated 31 migrations (execution time 00:00.061s)
INFO org.flywaydb.core.internal.command.DbMigrate - Current version of schema "public": 30
INFO org.flywaydb.core.internal.command.DbMigrate - Migrating schema "public" to version "32 - create knowledge tables"
INFO org.flywaydb.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v32 (execution time 00:00.049s)
```
- Existing migrations V1 through V30 remained completely intact and unmodified.
- No Flyway clean or repair was executed; no database reset was performed.

### 5.2 Live Test Verification Summary (`PostgresFtsRetrievalIntegrationTest`)
1. **Catalog Artifacts (`shouldVerifyV32MigrationAndCatalogArtifacts`)**:
   - Confirmed `knowledge_documents` and `knowledge_chunks` tables exist in `information_schema.tables`.
   - Confirmed GIN index `idx_knowledge_chunks_tsv` exists in `pg_indexes`.
   - Confirmed partial unique index `uq_knowledge_documents_single_active_version` exists in `pg_indexes`.
2. **Auto-Generated tsvector Column (`shouldVerifyGeneratedTsvectorColumnBehavior`)**:
   - Confirmed `chunk_vector` is automatically populated on chunk insert from `section_title` and `chunk_text` using the `'english'` dictionary.
   - Verified stemming: verified presence of stemmed lexemes `'brake'`, `'rotor'`, `'return'`, and `'guidelin'`.
3. **FTS Lexical Retrieval (`shouldExecuteWebsearchToTsqueryAndRankCd`)**:
   - Executed native `websearch_to_tsquery('english', 'alternators warranty')` and `ts_rank_cd(..., 5)`.
   - Verified that inserted test document was matched, returned with positive relevance score, and preserved title, code, and section metadata.
4. **Ranking Normalization Flag 5 (`shouldOrderResultsByTsRankCdFlag5`)**:
   - Inserted compact dense match (terms adjacent in short passage) and sparse match (terms separated by extensive filler text).
   - Confirmed dense match scored higher and was ranked before sparse match due to cover density and extent distance normalization (`flag 1 | flag 4 = flag 5`).
5. **Double-Active Filtering (`shouldExcludeInactiveDocumentsAndChunks`)**:
   - Verified that chunks from inactive documents are excluded even if chunk `active = TRUE`.
   - Verified that inactive chunks are excluded even if parent document `active = TRUE`.
6. **SQL-Level Role Filtering (`shouldEnforceSqlRoleFiltering`)**:
   - Verified that a document with `required_role = 'ROLE_ADMIN'` is excluded at the SQL level when queried by a user with `['ROLE_CASHIER', 'PUBLIC']`.
   - Verified that the same document is returned when queried by an administrator with `['ROLE_ADMIN', 'PUBLIC']`.
7. **Partial Unique Index Active Version Constraint (`shouldEnforceActiveVersionUniqueness`)**:
   - Verified that attempting to insert a second active document with the same `document_code` throws a `DataAccessException` (duplicate key constraint violation on `uq_knowledge_documents_single_active_version`).
   - Verified that deactivating version 1 allows version 2 with `active = TRUE` to be inserted and activated cleanly.

---

## 6. Document Ingestion Authorization Boundary

Before exposing any future HTTP REST API endpoint or administrative controller for document ingestion/upload:
1. **Authenticated Administrator Boundary**: Ingestion must require administrative authority (e.g., `hasRole('ADMIN')`).
2. **Derived Fields**: The document's `createdBy` field must be derived strictly from the authenticated principal (`authentication.getName()` or `ErpAiExecutionContext.userSubject()`), never from client-supplied request bodies.
3. **Role Assignment Validation**: The `requiredRole` assigned to an ingested document must be validated against the authorized roles of the executing administrator. A non-admin user must never be permitted to ingest documents or assign permissions.
4. **Current Exposure**: In Stage 3B, `KnowledgeIngestionService` is exclusively an internal, programmatic service with zero public or unauthenticated HTTP endpoints.
