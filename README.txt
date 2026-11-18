Stage 3C - Phase 4 - Task 1: Complete query preprocessing verification package

Extract this ZIP at the root of your existing autoparts-erp project.
It contains complete replacement source/test files using the project's existing package paths.

Files:
1) src/main/java/com/autoparts/erp/ai/rag/retrieval/KnowledgeQueryPreprocessor.java
2) src/test/java/com/autoparts/erp/ai/rag/retrieval/KnowledgeQueryPreprocessorTest.java
3) src/test/java/com/autoparts/erp/ai/rag/retrieval/PostgresFtsRetrievalIntegrationTest.java

What is covered:
- Natural-language negation is retained as metadata; it is never rewritten to PostgreSQL -term syntax.
- Constrained queries disable exact phrase, Boolean AND, and OR tiers at the preprocessor boundary.
- Positive re-entry after WITHOUT and exclusion scopes.
- Neither/nor and ambiguous scope handling.
- Hyphenated non-refundable compound preservation and ambiguous standalone NON handling.
- EXCEPT operator handling.
- PostgreSQL comparison of natural-language NOT and explicit FTS minus-term exclusion using three controlled text examples.
- Runtime PostgreSQL diagnostics for English tokenization and single-token plainto/websearch parsing of not, no, without, neither, nor.

Run from D:\Projects\autoparts-erp:

.\mvnw.cmd clean test -o -Dtest=KnowledgeQueryPreprocessorTest
.\mvnw.cmd test -o -Dtest=PostgresFtsRetrievalIntegrationTest

Expected unit test count: 23 (19 existing + 4 additional).
Expected integration test count: 14 (one existing integration test strengthened with additional assertions).

Important:
- These files are a Task 1 verification package only.
- No production retrieval-engine integration, Task 2 fallback implementation, SQL role filter, schema, Flyway migration, or Phase 3 evaluation data is changed.
- PostgreSQL integration test requires the project's configured local PostgreSQL datasource. It may skip if PostgreSQL is unavailable according to the existing test setup.
- Run both commands locally. This package has not been executed against your Windows project/database in this environment.
- Do not commit until Task 1 and the complete Phase 4 are approved.
