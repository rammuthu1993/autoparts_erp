# Stage 3C – Phase 4 – Tasks 2, 3 and 4

**Scope:** Multi-tier lexical retrieval, deterministic ranking/deduplication, and re-evaluation against the unchanged Phase 3 dataset.

## 1. Task 2 – Multi-tier retrieval

`PostgresFtsRetrievalEngine.retrieve(String, Set<String>, int)` now preprocesses the original query and executes these tiers for ordinary positive queries:

1. **Exact phrase:** quoted phrase via `websearch_to_tsquery('english', ...)`.
2. **Boolean AND:** all meaningful positive terms via `plainto_tsquery('english', ...)`.
3. **Weighted OR:** `OR` query via `websearch_to_tsquery('english', ...)`, with an additional score contribution from an informative-anchor query.

A later tier runs only when earlier tiers have not produced the requested number of unique chunks. A one-term query skips the phrase tier because phrase and AND matching are equivalent. Known constrained queries use AND followed, if needed, by the separate constraint-aware anchor tier described below; they never run the generic positive-query phrase/OR tiers.

### Weighted OR safety

The weighted OR tier is only available for ordinary positive queries. It requires at least one of the two longest positive terms (deterministically selected) to match. The anchor match contributes an additional ranking weight. This reduces fallback matches caused only by generic terms in out-of-scope queries.

For a known constrained query, the separate constraint-aware anchor tier uses the two longest positive terms when available; for a negative-only query it uses the two longest scoped content terms. The complete positive-plus-scoped content vocabulary remains in the OR query, and the selected anchor query is an additional gate and ranking signal.

### Constrained query safety

Natural-language negation and exclusion are not translated into PostgreSQL `-term` operators. For a constrained query with a known scope, the engine first searches positive and scoped content terms together as ordinary positive lexical anchors using the AND tier. If AND is insufficient, a separate **constraint-aware anchor tier** searches an OR of those same content terms and requires at least one of the two most informative anchors to match. This is not the preprocessor's generic OR fallback: the preprocessor's `orQuery` remains empty for constrained questions, and the engine never discards the negation/exclusion metadata or applies a document-level negative filter.

The anchor tier retrieves evidence about the terms mentioned in the negative question; it does not mean that a returned document satisfies the negated condition. The original natural-language question is not rewritten by the engine; the RAG answer layer continues to receive the original user question. If the preprocessor marks the scope ambiguous, retrieval fails closed and returns no chunks.

## 2. Task 3 – Deterministic ranking and deduplication

Every SQL tier applies:

```sql
ORDER BY relevance_score DESC, c.chunk_index ASC, c.id ASC
```

All tiers preserve the same filters:

- `c.active = TRUE`
- `d.active = TRUE`
- `d.required_role IN (:allowedRoles)`

The merge layer:

1. Deduplicates by stable `chunk_id`.
2. Keeps the strongest retrieval tier for a duplicate.
3. Orders by tier priority (phrase, AND, constrained anchor, weighted OR), then tier score descending, `chunk_index` ascending, and `chunk_id` ascending.
4. Applies the final top-K limit after deduplication.

`RetrievedChunk` and the database schema remain unchanged. The returned `relevanceScore` remains the raw FTS score for its tier; final cross-tier ordering is represented by tier priority and deterministic merge order.

## 3. Task 4 – Phase 3 benchmark re-run

The existing `KnowledgeRetrievalEvaluationTest` continues to load the original 28-query dataset and now runs through the optimized `engine.retrieve(...)` pipeline.

A dataset integrity test verifies:

- Exactly 28 evaluation cases.
- Original SHA-256: `96259797f23c595a95857eef18345aff06478681fe37ef0d7e12df0993069030`.

The benchmark prints the optimized metrics alongside the Phase 3 baseline:

| Metric | Phase 3 baseline |
|---|---:|
| Document Hit Rate | 78.3% |
| Section Hit Rate | 69.6% |
| MRR | 0.6232 |
| Adaptive Precision@3 | 0.5507 |
| Recall@5 | 0.6377 |
| Negative zero-result rate | 100% |
| Role isolation violations | 0 |
| Average latency | 0.81 ms |

No arbitrary minimum improvement is asserted for relevance metrics. The strict invariants remain 100% negative/role-blocked zero-result behavior and zero authorization leakage. Latency is measured because fallback tiers may add database calls.

## 4. Files changed

- `src/main/java/com/autoparts/erp/ai/rag/retrieval/PostgresFtsRetrievalEngine.java`
- `src/test/java/com/autoparts/erp/ai/rag/retrieval/KnowledgeRetrievalEngineTest.java`
- `src/test/java/com/autoparts/erp/ai/rag/retrieval/PostgresFtsRetrievalIntegrationTest.java`
- `src/test/java/com/autoparts/erp/ai/rag/retrieval/KnowledgeRetrievalEvaluationTest.java`
- `src/main/java/com/autoparts/erp/ai/rag/retrieval/KnowledgeQueryPreprocessor.java` (included unchanged from the Task 1 version already verified)
- `src/test/java/com/autoparts/erp/ai/rag/retrieval/KnowledgeQueryPreprocessorTest.java` (included unchanged from the Task 1 version already verified)

No changes are made to:

- Phase 3 evaluation JSON dataset.
- Flyway migrations or database schema.
- Role hierarchy or SQL authorization predicates.
- Citation validation.
- `KnowledgeRetrievalService` API or `RetrievedChunk` record.

## 5. Verification commands

Run from the project root in PowerShell:

```powershell
.\mvnw.cmd clean test -o -Dtest=KnowledgeQueryPreprocessorTest,KnowledgeRetrievalEngineTest
```

```powershell
.\mvnw.cmd test -o -Dtest=PostgresFtsRetrievalIntegrationTest
```

```powershell
.\mvnw.cmd test -o -Dtest=KnowledgeRetrievalEvaluationTest
```

The two PostgreSQL-backed suites must run against the same safe local test database configuration used for Phase 3. Do not enable Flyway migration for this task; the existing V32 schema is sufficient.

## 6. Phase gate

Do not approve Phase 4 until the full 28-query report has been reviewed, negative/role-blocked cases remain at 100% zero-result rate, role-isolation violations remain zero, and the latency trade-off is accepted. Do not commit until all four Phase 4 tasks are approved.
