# Stage 3C — Phase 1: Representative Knowledge Document Corpus Report

**Status**: COMPLETED & VERIFIED  
**Date**: 2026-11-18  
**Scope**: Synthetic Knowledge Document Corpus Creation & Parser Verification for Lexical RAG  
**Target Environment**: Java 25, Apache PDFBox 3.x, Spring JDBC, PostgreSQL 18.4

---

## 1. Executive Summary

Phase 1 of Stage 3C establishes a deterministic, multi-format, synthetic evaluation corpus for the Auto Parts ERP lexical RAG pipeline. The corpus provides realistic automotive aftermarket business documentation spanning customer warranties, cashier cash register operations, parts returns and core exchanges, and GST invoicing rules across three distinct content formats (Markdown, Plain Text, and Multi-page PDF).

All fixtures strictly comply with the document ingestion contract established in Stage 3B and have been verified via a focused test suite ([KnowledgeEvaluationCorpusTest.java](file:///d:/Projects/autoparts-erp/src/test/java/com/autoparts/erp/ai/rag/ingestion/KnowledgeEvaluationCorpusTest.java)).

> [!IMPORTANT]
> **SYNTHETIC TEST DATA DISCLAIMER**: All documents in this evaluation corpus are synthetic test fixtures created exclusively for automated RAG testing, chunking evaluation, and retrieval benchmarking. All warranty periods, dollar/currency amounts, restocking fees, core deposits, operating procedures, and tax rules are entirely fictional and do not represent approved corporate policies, binding contractual terms, or professional tax/accounting advice.

---

## 2. Evaluation Corpus Inventory

The corpus consists of four distinct fixtures stored under `src/test/resources/knowledge/`:

| Document Code | Document Title | Document Type | Content Format | Required Role | File Name | File Size | Key Topic Areas |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POL-WAR-001` | Auto Parts Warranty Policy | `POLICY` | `MARKDOWN` | `PUBLIC` | `POL-WAR-001.md` | 7,016 B | Batteries, brake rotors, alternators, sensors, claim procedure, manager sign-off |
| `SOP-CSH-001` | Daily Cash Register Closing & Reconciliation SOP | `SOP` | `TEXT` | `ROLE_CASHIER` | `SOP-CSH-001.txt` | 7,527 B | Opening float, mid-day drop, drawer counting, payment reconciliation, discrepancy thresholds |
| `POL-RET-001` | Return & Core Exchange Policy | `POLICY` | `PDF` | `PUBLIC` | `POL-RET-001.pdf` | 3,581 B | 3-page layout: return window, condition, restocking fees, battery core deposits, alternator cores |
| `FAQ-GST-001` | ERP Billing & GST Clarification FAQ | `FAQ` | `MARKDOWN` | `ROLE_CASHIER` | `FAQ-GST-001.md` | 6,046 B | Taxable value vs. subtotal, auto parts HSN codes, CGST/SGST/IGST split, debit/credit notes |

---

## 3. Fixture Detailed Specifications

### 3.1 `POL-WAR-001.md` — Auto Parts Warranty Policy
- **Purpose**: Evaluates Markdown heading hierarchy inheritance across three levels (`#` Document Title, `##` Major Section, `###` Component Category) and retrieval of component-specific warranty parameters.
- **Coverage**:
  - *Automotive Batteries*: 24-month coverage (12-month free replacement, 13–24 pro-rata), mandatory automated conductance analyzer test receipt, casing/melted post exclusions.
  - *Brake Rotors & Friction*: 12-month / 15,000 km defect coverage, runout threshold (0.05 mm), uneven wear and commercial fleet exclusions.
  - *Alternators & Starters*: 36-month limited warranty, bench testing criteria (< 13.5V under load), intact core aluminum housing requirement.
  - *Electrical Sensors*: 12-month replacement, technician installation requirement, electrical burnout and scan tool diagnostic report prerequisites.
  - *Exclusions & Workflow*: Disassembly/racing exclusions, Form WAR-01 claim submission, 24-hour technical verification, branch manager ERP sign-off, replacement or store credit issuance (no cash refunds).

### 3.2 `SOP-CSH-001.txt` — Daily Cash Register Closing & Reconciliation SOP
- **Purpose**: Evaluates plain-text section parsing, paragraph boundary snapping, and role-restricted operational procedure retrieval for cashier staff.
- **Coverage**:
  - *Section 1*: Opening float verification (exact 5,000 currency units with denomination breakdown).
  - *Section 2*: Mid-day cash drop procedure (20,000-unit threshold, dual-signed envelope, anti-fish depository safe).
  - *Section 3*: End-of-day cash counting (segregation of denominations, optical counting machine verification, net sales calculation).
  - *Section 4*: Payment-method reconciliation (EDC card settlement batches, UPI digital wallet receipts, customer credit accounts, cheques).
  - *Section 5*: Discrepancy thresholds ($\le 50$ units cashier note, 51–500 units manager recount, $> 500$ units security incident investigation).
  - *Section 6*: Manager approval PIN, tamper-evident pouch sealing, safe deposit, and finance audit binder filing.

### 3.3 `POL-RET-001.pdf` — Return & Core Exchange Policy
- **Purpose**: Evaluates multi-page PDF reading order text extraction using Apache PDFBox 3.x, ensuring text across distinct pages is captured into searchable chunks.
- **Structure**:
  - *Page 1*: Standard 30-day return window, original invoice requirement, 31–60 day store credit rule, product condition, and intact packaging criteria.
  - *Page 2*: Restocking fees (0% standard in-stock, 15% special order items, 0% catalog error waiver), battery core deposits (750 currency units, 45-day surrender window, casing criteria).
  - *Page 3*: Alternator and starter core exchange (1,200 currency units deposit, like-for-like frame family, crack-free aluminum housings, 30-day window), non-returnable items (opened electronic modules, cut-to-length hoses, fluids).
- **Generation Utility**: Generated deterministically via [GeneratePdfCorpusFixture.java](file:///d:/Projects/autoparts-erp/src/test/java/com/autoparts/erp/ai/rag/ingestion/GeneratePdfCorpusFixture.java) utilizing Apache PDFBox 3.x Standard 14 Helvetica fonts.

### 3.4 `FAQ-GST-001.md` — ERP Billing & GST Clarification FAQ
- **Purpose**: Evaluates question-and-answer format retrieval for commercial tax and ERP billing concepts.
- **Coverage**:
  - *Section 1*: Gross Subtotal vs. Taxable Value mathematical formula, MRP back-calculation formula.
  - *Section 2*: Harmonized System of Nomenclature (HSN) purpose, specific automotive codes (`8708` chassis/brakes 28%, `8511` ignition/starters 18%, `8507` batteries 28%, `8421` filters 18%, `4016` rubber belts 18%), 6-digit B2B vs 4-digit B2C reporting rules.
  - *Section 3*: Place of Supply tax split (Intra-state CGST + SGST vs. Inter-state IGST), invoice rounding differential to `INVOICE_ROUNDING` ledger.
  - *Section 4*: Credit note mandatory conditions (returns, rate reductions, damaged goods) vs. Debit note conditions (under-billing corrections, supplier returns).

---

## 4. Ingestion Contract Alignment

All four fixtures conform to `KnowledgeIngestionService.IngestionRequest`:
- **Document Codes**: Valid uppercase alphanumeric string matching `^[A-Za-z0-9_-]{3,100}$`.
- **Whitelisted Values**:
  - `documentType`: strictly member of `('POLICY', 'SOP', 'MANUAL', 'GUIDELINE', 'HANDBOOK', 'SPECIFICATION', 'FAQ')`.
  - `contentFormat`: strictly member of `('PDF', 'TEXT', 'MARKDOWN')`.
  - `requiredRole`: strictly member of `('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CASHIER', 'PUBLIC')`.
- **Extension Matching**: `POL-WAR-001.md` (`.md`), `SOP-CSH-001.txt` (`.txt`), `POL-RET-001.pdf` (`.pdf`), `FAQ-GST-001.md` (`.md`).
- **File Safety**: Clean UTF-8 bytes for text/markdown (zero null bytes); valid `%PDF-` header magic bytes for PDF; zero path traversal in `fileName`.

---

## 5. Verification & Test Suite Summary

A dedicated test class [KnowledgeEvaluationCorpusTest.java](file:///d:/Projects/autoparts-erp/src/test/java/com/autoparts/erp/ai/rag/ingestion/KnowledgeEvaluationCorpusTest.java) executes 7 focused verification tests:
1. `shouldEnsureAllFourCorpusFixturesExistAndAreNonEmpty`: Verifies physical existence and substantial size (> 500 B) of all four fixtures.
2. `shouldParseMarkdownWarrantyPolicyAndPreserveHeadingHierarchy`: Verifies multi-level `# Policy > ## Category > ### Component` heading inheritance in chunk drafts.
3. `shouldParsePlainTextCashSopAndMaintainUsableSectionBoundaries`: Verifies plain-text parsing and clean paragraph chunk boundaries.
4. `shouldParseMultiPagePdfReturnPolicyAndExtractSectionsAcrossPages`: Verifies PDFBox 3.x reading order text extraction spanning Page 1, Page 2, and Page 3.
5. `shouldParseMarkdownGstFaqAndPreserveHeadingHierarchy`: Verifies Q&A heading hierarchy inheritance across all four FAQ topics.
6. `shouldValidateIngestionRequestsForEntireCorpus`: Verifies `IngestionRequest` record construction, metadata mapping, and SHA-256 hash calculation.
7. `shouldVerifySyntheticDisclaimersInAllFixtures`: Verifies mandatory presence of `"SYNTHETIC TEST FIXTURE"` disclaimer in every document.

### Test Execution Counts
Full suite execution (`mvnw.cmd clean test -o "-Dtest=!PostgresQueryTimeoutIntegrationTest"`):

$$\text{Total Test Suite} = 82\text{ (Stage 3 RAG & Ingestion)} + 314\text{ (Stage 2B–2E AI Agent)} + 19\text{ (Non-AI Core)} = 415\text{ Tests}$$

- **Classes**: 39 test classes
- **Executed**: **415 tests, 415 passed, 0 failures, 0 errors, 0 skipped (100.0% pass rate)**
- **Surefire Reports**: Exactly 39 XML report files in `target/surefire-reports`, perfectly reconciling 1:1.

---

## 6. Implementation of `PostgresFtsRetrievalIntegrationTest` Test-Harness Corrections

The requested test-harness corrections in [PostgresFtsRetrievalIntegrationTest.java](file:///d:/Projects/autoparts-erp/src/test/java/com/autoparts/erp/ai/rag/retrieval/PostgresFtsRetrievalIntegrationTest.java) have been implemented and verified:

| Safety Principle | Implementation Details | Verification Status |
| :--- | :--- | :--- |
| **1. Unintended Migrations (.target("32"))** | Configured Flyway strictly with `.target("32")` in `createFlywayMigrationConfig()`. Guarantees opt-in runs cannot apply future migrations beyond V32. | **VERIFIED**: Unit test `shouldConfigureFlywayTargetRestrictedToV32()` asserts target version is `"32"`. |
| **2. Dynamic Host/Port Parsing & Reachability Probe** | Replaced hardcoded `localhost:5432` probe with `parseHostAndPort(dbUrl)` which extracts hostname and port (defaulting to 5432 if omitted) across formats (standard host:port, IPv6 `[::1]`, default port, query params). Probes socket reachability using parsed host and port. Once reachable, authentication failures (`SQLException`), migration failures (`FlywayException`), and schema query failures (`DataAccessException`) fail fast by throwing `RuntimeException` with root cause preserved. | **VERIFIED**: Unit tests `shouldParseHostAndPortFromPostgreSqlJdbcUrl()` (tests localhost, 127.0.0.1, remote host, IPv6, query parameters), `shouldDetectUnreachableServicePortGracefully()` (verifies offline port detection on parsed hosts), and `shouldFailFastOnAuthenticationFailureWhenServiceIsReachable()` (verifies fast failure on invalid credentials). |
| **3. Remote Database Access Guard & Explicit Opt-In** | Preserved default local-only policy (`localhost`, `127.0.0.1`, `::1`). Remote database targets require explicit opt-in via `-Dtest.allow.remote.db=true`. Production guard (`prod`, `production`, `live-db` markers and `spring.profiles.active=prod`) is strictly enforced unconditionally and is NEVER bypassed even when remote opt-in is active. | **VERIFIED**: Unit tests `shouldRejectProductionDatabaseTargets()` and `shouldAllowLocalhostAnd127001AndRemoteHostWithExplicitOptIn()` validate local allowance, remote rejection without opt-in, remote acceptance with opt-in, and strict production rejection under all configurations. |
| **4. Zero Destructive Operations** | Zero Flyway `clean()`, `repair()`, or table drops. Isolated DML operations with cleanup in `finally` blocks. | **VERIFIED**: Preserved across all 7 live database tests. |
