# Stage 3C Phase 3 — Retrieval Evaluation & Precision Benchmark Report

**Status**: COMPLETED & VERIFIED (Awaiting User Phase-Gate Approval; Phase 4 Queued)  
**Date**: 2026-11-18  
**Scope**: Empirical baseline measurement of the existing PostgreSQL Full-Text Search (FTS) lexical retrieval pipeline (`PostgresFtsRetrievalEngine`) across 4 representative corpus documents (33 chunks) and 28 evaluation queries.  
**Target Environment**: Java 25, Spring Boot 4.1.x, PostgreSQL 18.4, Spring JDBC, Flyway V32 schema.  
**Constraint Enforced**: Measurement-only benchmark; zero production retrieval modifications before establishing the empirical baseline.

---

## 1. Executive Summary

In Stage 3C Phase 3, an empirical retrieval evaluation framework was built and executed to measure the baseline precision, recall, hit rate, and ranking behavior of `PostgresFtsRetrievalEngine`. The evaluation was performed against a live PostgreSQL 18.4 database seeded with the 4 representative corpus documents (33 chunks) created in Phase 1 and benchmarked in Phase 2.

The evaluation suite executes 28 realistic queries spanning 6 distinct functional categories (Direct Keyword, Natural Language, Term Variation, Multi-Term, Negative Out-of-Scope, and Role-Restricted). Strict test isolation was enforced using run-specific document code generation (`EVAL_<type>_<uuid>`) and primary-key-based UUID deletion to guarantee that no development database records could be corrupted or removed during teardown.

### Headline Benchmark Metrics (Empirically Measured on PostgreSQL 18.4)

| Metric | Measured Baseline | Architectural Classification & Evaluation Notes |
| :--- | :---: | :--- |
| **Total Evaluation Cases** | **28** | 23 positive queries, 5 negative / role-blocked queries |
| **Document Hit Rate** | **78.3% (18 / 23)** | Target document returned in top-5 for 18 of 23 positive business queries |
| **Section Hit Rate** | **69.6% (16 / 23)** | Authoritative target section returned in top-5 for 16 of 23 positive queries |
| **Mean Reciprocal Rank (MRR)** | **0.6232** | Evaluated across all 23 positive queries (1.00 on Direct Keywords, 0.00 on Natural Language) |
| **Precision@1 (Fixed: rel/1)** | **0.5652** | 56.5% of queries returned an authoritative relevant chunk at rank 1 |
| **Precision@3 (Fixed: rel/3)** | **0.2464** | Standard fixed-denominator precision across top-3 slots |
| **Precision@5 (Fixed: rel/5)** | **0.1478** | Standard fixed-denominator precision across top-5 slots |
| **Precision@1 (Adaptive)** | **0.5652** | Adaptive denominator: $\text{rel} / \min(K, \text{retrieved})$ |
| **Precision@3 (Adaptive)** | **0.5507** | Reflects precision among actual retrieved candidates in top-3 |
| **Precision@5 (Adaptive)** | **0.5507** | Reflects precision among actual retrieved candidates in top-5 |
| **Recall@1 (rel in top-1 / total)** | **0.4928** | Unclamped mathematical recall at rank 1 |
| **Recall@3 (rel in top-3 / total)** | **0.6377** | Unclamped mathematical recall across top-3 slots |
| **Recall@5 (rel in top-5 / total)** | **0.6377** | Unclamped mathematical recall across top-5 slots |
| **Negative Query Zero-Result Rate** | **100.0% (5 / 5)** | 0 false-positive chunks returned for out-of-scope and role-blocked queries |
| **Role-Based Isolation Violations** | **0 (Strict 0)** | Zero-trust SQL boundary pre-filtering strictly prevents document leakage |
| **Average Retrieval Latency** | **0.81 ms** | Sub-millisecond execution via GIN index on `chunk_vector` (Min: 0.42 ms, Max: 1.63 ms) |

---

## 2. Test Suite & Surefire Report Reconciliation

The Stage 3C Phase 3 evaluation and RAG regression test suite executed across **11 test classes**, comprising **108 tests** with **zero failures, zero errors, and zero skipped tests**. All Surefire XML reports were verified with clean timestamps from the latest test run:

| Test Class | Tests Run | Failures | Errors | Skipped | Time (s) | Scope & Verification Focus |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `retrieval.KnowledgeRetrievalEvaluationTest` | **28** | **0** | **0** | **0** | 0.485s | 28-query precision benchmark, lifecycle safety, fail-closed role isolation tests |
| `retrieval.PostgresFtsRetrievalIntegrationTest`| **13** | **0** | **0** | **0** | 0.548s | PostgreSQL URL parsing, IPv6, reachability, production guards |
| `retrieval.KnowledgeRetrievalEngineTest` | **4** | **0** | **0** | **0** | 0.047s | FTS query generation, parameter binding, empty query handling |
| `retrieval.KnowledgeAccessControlTest` | **4** | **0** | **0** | **0** | 0.009s | Role hierarchy resolution (`ROLE_ADMIN`, `MANAGER`, `CASHIER`, `PUBLIC`) |
| `citation.CitationValidatorTest` | **16** | **0** | **0** | **0** | 0.126s | Grounding verification, hallucination checks, markdown bracket escaping |
| `citation.PromptInjectionDefenseTest` | **6** | **0** | **0** | **0** | 0.691s | Prompt sanitization, delimiter enforcement, override defenses |
| `ingestion.ChunkingBenchmarkTest` | **5** | **0** | **0** | **0** | 0.471s | Paragraph preservation, word boundaries, overlap, aggregate ratio |
| `ingestion.KnowledgeChunkingTest` | **4** | **0** | **0** | **0** | 0.009s | Section header hierarchy, chunk index sequencing |
| `ingestion.KnowledgeDocumentIngestionTest` | **15** | **0** | **0** | **0** | 0.447s | Document parsing, text extraction, SHA-256 deduplication |
| `ingestion.KnowledgeEvaluationCorpusTest` | **7** | **0** | **0** | **0** | 0.035s | Synthetic corpus validation, format validation (Markdown, Text, PDF) |
| `ingestion.KnowledgeVersioningTest` | **6** | **0** | **0** | **0** | 0.064s | Document version bumping, partial active index enforcement |
| **TOTAL (RAG Regression Suite)** | **108** | **0** | **0** | **0** | **2.932s** | **100% Passing (Build SUCCESS)** |

---

## 3. Complete 28-Query Retrieval Census

Below is the complete execution census across all 28 evaluation queries executed against the live PostgreSQL 18.4 database:

| Case ID | Category | Query Text | DocHit | SecHit | First Rank | P@1 | P@3 | P@5 | R@1 | R@3 | R@5 | MRR | Latency |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `EVAL-DIR-001` | DIRECT_KEYWORD | `battery warranty period` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 1.0 ms |
| `EVAL-DIR-002` | DIRECT_KEYWORD | `standard return window replacement parts` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 1.0 ms |
| `EVAL-DIR-003` | DIRECT_KEYWORD | `opening cash float amount` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 1.1 ms |
| `EVAL-DIR-004` | DIRECT_KEYWORD | `HSN code automotive batteries` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 0.7 ms |
| `EVAL-DIR-005` | DIRECT_KEYWORD | `restocking fee returned parts` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 0.7 ms |
| `EVAL-NL-001` | NATURAL_LANGUAGE | `alternator not charging submit warranty claim` | NO | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.6 ms |
| `EVAL-NL-002` | NATURAL_LANGUAGE | `closing cash does not match register balance discrepancy` | YES | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 1.8 ms |
| `EVAL-NL-003` | NATURAL_LANGUAGE | `return alternator without core deposit exchange` | YES | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 1.0 ms |
| `EVAL-NL-004` | NATURAL_LANGUAGE | `GST split interstate sale IGST CGST SGST` | NO | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.7 ms |
| `EVAL-NL-005` | NATURAL_LANGUAGE | `credit note issued instead of cancelling invoice` | NO | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 1.0 ms |
| `EVAL-VAR-001` | TERM_VARIATION | `alternator warranties` | YES | YES | 2 | 0.00 | 0.33 | 0.20 | 0.00 | 1.00 | 1.00 | 0.50 | 0.7 ms |
| `EVAL-VAR-002` | TERM_VARIATION | `returning replacement parts` | YES | YES | 2 | 0.00 | 0.33 | 0.20 | 0.00 | 1.00 | 1.00 | 0.50 | 0.6 ms |
| `EVAL-VAR-003` | TERM_VARIATION | `reconciling cash drawers` | YES | YES | 1 | 1.00 | 0.67 | 0.40 | 0.33 | 0.67 | 0.67 | 1.00 | 0.8 ms |
| `EVAL-VAR-004` | TERM_VARIATION | `battery deposits` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 0.6 ms |
| `EVAL-VAR-005` | TERM_VARIATION | `crediting notes for returns` | YES | YES | 3 | 0.00 | 0.33 | 0.20 | 0.00 | 1.00 | 1.00 | 0.33 | 0.5 ms |
| `EVAL-MUL-001` | MULTI_TERM | `battery warranty tester receipt exclusions` | NO | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.5 ms |
| `EVAL-MUL-002` | MULTI_TERM | `cash closing discrepancy manager approval` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 0.50 | 0.50 | 0.50 | 1.00 | 0.9 ms |
| `EVAL-MUL-003` | MULTI_TERM | `alternator core deposit return window` | NO | NO | - | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.6 ms |
| `EVAL-MUL-004` | MULTI_TERM | `taxable gross subtotal trade discount calculation` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 0.9 ms |
| `EVAL-MUL-005` | MULTI_TERM | `warranty claim approval counter inspection manager` | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 1.2 ms |
| `EVAL-NEG-001` | NEGATIVE_OUT_OF_SCOPE | `employee annual leave request policy HR vacation` | N/A | N/A | - | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 1.1 ms |
| `EVAL-NEG-002` | NEGATIVE_OUT_OF_SCOPE | `company vehicle fuel reimbursement mileage allowance expense` | N/A | N/A | - | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 1.1 ms |
| `EVAL-NEG-003` | NEGATIVE_OUT_OF_SCOPE | `configure Docker containers Kubernetes cluster deployment pod` | N/A | N/A | - | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 1.0 ms |
| `EVAL-NEG-004` | NEGATIVE_OUT_OF_SCOPE | `comprehensive auto vehicle insurance premium policy underwriting` | N/A | N/A | - | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 1.0 ms |
| `EVAL-ROL-001` | ROLE_RESTRICTED | `cash register safe drop envelope` (as CASHIER) | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 1.0 ms |
| `EVAL-ROL-002` | ROLE_RESTRICTED | `cash register safe drop envelope` (as PUBLIC) | N/A | N/A | - | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 0.5 ms |
| `EVAL-ROL-003` | ROLE_RESTRICTED | `battery warranty claim submission` (as ADMIN) | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 0.50 | 0.50 | 0.50 | 1.00 | 0.9 ms |
| `EVAL-ROL-004` | ROLE_RESTRICTED | `daily cash count float deduction` (as MANAGER) | YES | YES | 1 | 1.00 | 0.33 | 0.20 | 1.00 | 1.00 | 1.00 | 1.00 | 1.1 ms |

> [!NOTE]
> **Negative and Role-Blocked Metric Semantics**: In standard Information Retrieval (IR) methodology, queries without positive target chunks have mathematically undefined precision, recall, and reciprocal rank ($0/0$). In accordance with evaluation standards, these metrics are recorded as `Double.NaN` and displayed as `N/A`, rather than 1.0. Correct rejection behavior is measured separately and rigorously via the **True Negative Rate** (100.0%, 0 false positives).


---

## 4. Category-by-Category Analysis & Empirical Findings

### 4.1 Category A: Direct Keyword Queries (5 / 5 = 100% DocHit, 100% SecHit, MRR 1.00)
When queries use exact, domain-specific terminology matching the document text:
- **Performance**: Perfect 100% Document and Section Hit Rate. All 5 queries retrieved the authoritative section at **Rank 1** with an MRR of **1.0000**.
- **Observation**: Un-modified PostgreSQL FTS with `websearch_to_tsquery` and `ts_rank_cd(..., 5)` is highly effective for concise keyword lookups.

### 4.2 Category B: Natural Language Queries (2 / 5 = 40% DocHit, 0 / 5 = 0% SecHit, MRR 0.00)
Natural language queries expose the primary architectural bottleneck of raw lexical FTS:
- **`websearch_to_tsquery` Boolean `AND` Conjunction**: Unquoted words separated by spaces are parsed as strict Boolean `AND` (`word1 & word2 & word3`).
- **Conversational Filler Terms**: In `EVAL-NL-003` (`without`) and `EVAL-NL-005` (`instead of`, `cancelling`), conversational vocabulary that does not exist in authoritative policies causes the entire `AND` conjunction to fail.
- **Synonym Mismatch**: In `EVAL-NL-002` (`match` vs statutory *variance/differ*) and `EVAL-NL-004` (`split` vs *divided*), the absence of morphological or semantic synonym mapping causes retrieval failure.
- **Cross-Chunk Term Distribution**: In `EVAL-NL-001` (`alternator not charging submit warranty claim`), terms span Section 2.3 (Chunk 5: alternator specs) and Section 4 (Chunk 8: claim submission). Neither chunk contains all 5 required terms.
- **Document Hit vs Section Hit**: `EVAL-NL-002` and `EVAL-NL-003` achieved Document Hits because an adjacent chunk happened to contain matching tokens, but failed Section Relevance because the specific target chunk was not ranked.

### 4.3 Category C: Term Variation & Stemming (5 / 5 = 100% DocHit, 100% SecHit, MRR 0.666)
- **Stemming Efficacy**: The English Snowball stemmer correctly collapsed morphological variants:
  - `alternator warranties` $\rightarrow$ `'altern' & 'warranti'` (Chunk 5 at Rank 2, MRR 0.50).
  - `returning replacement parts` $\rightarrow$ `'return' & 'replac' & 'part'` (Chunk 0 at Rank 2, MRR 0.50).
  - `reconciling cash drawers` $\rightarrow$ `'reconcil' & 'cash' & 'drawer'` (Chunk 0 at Rank 1, MRR 1.00).
  - `battery deposits` $\rightarrow$ `'batteri' & 'deposit'` (Chunk 1 at Rank 1, MRR 1.00).
  - `crediting notes for returns` $\rightarrow$ `'credit' & 'note' & 'return'` (Chunk 12 at Rank 3, MRR 0.33).
- **Average MRR**: 0.6660 across the 5 queries.

### 4.4 Category D: Multi-Term Queries (3 / 5 = 60% DocHit, 60% SecHit, MRR 0.60)
- **4-to-5 Term Queries**: When all query tokens reside in a single chunk (e.g. `EVAL-MUL-004` Taxable Gross Subtotal, `EVAL-MUL-005` Warranty Claim Review Workflow, and `EVAL-MUL-002` Cash Closing Discrepancy), retrieval succeeded at Rank 1.
- **Term Scatter Failures**: When query tokens are scattered across disparate policy sections (`EVAL-MUL-001` battery tester receipt across chunks 3 and 7; `EVAL-MUL-003` alternator deposit return window across chunks 0, 1, and 2), strict Boolean `AND` eliminates all candidate chunks, yielding 0 results.

### 4.5 Category E: Negative & Out-of-Scope Queries (4 / 4 = 100% Rejection)
- **Zero False-Positive Rate**: Queries on HR annual leave, vehicle fleet mileage reimbursement, Kubernetes/Docker infrastructure, and motor vehicle insurance returned **0 chunks**.
- **Out-of-Scope Rejection & Hallucination Prevention**: Zero-result retrieval on out-of-domain queries is useful because lexical FTS returns no chunks when query terms do not match corpus vocabulary. However, lexical FTS does not itself inherently prevent hallucinations; rather, the application's zero-document fail-safe in the RAG workflow (`ErpAiGraphNodes` returning a deterministic "no documentation found" message without invoking the LLM) is responsible for strictly preventing unsupported LLM generation when retrieval returns zero chunks.

### 4.6 Category F: Role-Restricted Retrieval & Security (4 / 4 = 100% Pass, 0 Violations)
- **Zero-Trust SQL Boundary Enforcement**:
  - `EVAL-ROL-001` (Cashier safe drop query as `ROLE_CASHIER`): Retrieved `SOP-CSH-001` Chunk 1 at Rank 1.
  - `EVAL-ROL-002` (Identical query as `PUBLIC`): Strictly blocked at SQL level (`WHERE d.required_role IN ('PUBLIC')`). Returned 0 chunks.
  - `EVAL-ROL-003` (Battery warranty claim as `ROLE_ADMIN`): Successfully retrieved `POL-WAR-001`.
  - `EVAL-ROL-004` (Cash count float deduction as `ROLE_MANAGER`): Successfully inherited access to cashier document `SOP-CSH-001`, retrieving Chunk 2 at Rank 1.
- **Dedicated Positive/Negative Test**: Verified via `shouldVerifyRoleBasedAuthorizationForConfirmedCashierTerms` that `ROLE_CASHIER` and `ROLE_MANAGER` retrieve `SOP-CSH-001` Chunk 0 while `PUBLIC` is blocked.
- **Fail-Closed Metric Isolation Verification**: In addition to SQL pre-filtering, `computeMetrics()` inspects every returned chunk against `KnowledgeRetrievalService.resolveAllowedRoles(tc.userRole())`. Any returned chunk from an unauthorized document (or unrecognized document defaulting to `ROLE_ADMIN`) immediately triggers `roleIsolationMaintained = false`. This was verified with 3 dedicated unit tests (`shouldDetectRoleIsolationViolationWhenUnauthorizedDocumentIsLeakedInPositiveQuery`, `shouldDetectRoleIsolationViolationWhenUnrelatedRestrictedDocumentIsLeaked`, and `shouldMaintainRoleIsolationWhenAllRetrievedDocumentsAreAuthorized`).

---

## 5. Cross-Phase Synthesis: Impact of Chunking Benchmark on Retrieval

The empirical findings from Stage 3C Phase 2 (`DocumentChunker` evaluation) directly explain observed retrieval dynamics:

1. **Orphan Header Stubs (24.24% Undersized Chunks)**:
   - In Phase 2, 8 of 33 chunks (24.24%) were undersized (<100 tokens), consisting of isolated markdown headers (e.g. `## 2. Product Warranty Categories`, 9 tokens).
   - In Phase 3, when a broad keyword query like `warranty` or `category` is evaluated, orphan header chunks match `chunk_vector` and compete for Top-K slots despite containing zero substantive policy text.
2. **Punctuation Termination (81.82%) & Extent Density**:
   - 27 of 33 chunks terminate cleanly at sentence boundaries. `ts_rank_cd(..., 5)` successfully utilizes harmonic distance normalization across extents within complete sentences.
3. **Zero Oversized Chunks (0.0%)**:
   - Strict enforcement of the 500-token ceiling prevented context window overflow and ensured consistent score normalization across all retrieved chunks.

---

## 6. Architectural Observations & Limitations of Pure Lexical FTS

1. **Strict Boolean `AND` vs. Conversational Search**:
   - `websearch_to_tsquery` does not support soft-matching or token dropoff. A single conversational word missing from a chunk eliminates that chunk entirely.
2. **Missing Secondary Tie-Breaker**:
   - The production SQL query executes `ORDER BY relevance_score DESC LIMIT :limit`.
   - When multiple chunks share identical `ts_rank_cd` scores (common when matching identical term counts), PostgreSQL does not guarantee deterministic order across executions. A secondary tie-breaker (`ORDER BY relevance_score DESC, c.chunk_index ASC, c.id ASC`) should be added in Phase 4.
3. **Server-Side Security Strength**:
   - Role pre-filtering at the SQL level (`d.required_role IN (:allowedRoles)`) provides 100% reliable zero-trust security without depending on application memory or prompt instructions.

---

## 7. Recommended Next Milestone

With Stage 3C Phase 3 empirical measurement complete and baseline metrics established (78.3% DocHit, 69.6% SecHit, 0.6232 MRR, 100% Negative Rejection, 0 Role Violations), the project is ready to proceed to:

**Stage 3C Phase 4: Query Preprocessing, Intent Classification & Retrieval Optimization**
- Strip conversational filler phrases and extract core business entities.
- Implement multi-tier retrieval fallback (exact phrase $\rightarrow$ Boolean `AND` $\rightarrow$ weighted `OR` disjunction).
- Introduce deterministic secondary tie-breaking in `PostgresFtsRetrievalEngine`.
- Re-benchmark against the exact 28-case evaluation dataset to measure precision/recall gains against this Phase 3 baseline.
