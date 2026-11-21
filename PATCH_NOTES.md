# Targeted AI fixes from the 2026-11-21 archive

## Included source changes

1. `ErpAiIntentClassifier.java`
   - Strip natural-language search qualifiers `matching`, `containing`, and `with` after removing the request verb and entity name.
   - Correct the unsupported-mutation regex so `closing` is a complete token and is not concatenated with unrelated search qualifiers.
   - Remove the obsolete generic-stock pattern and dead commented implementation.
2. `ProductLookupTool.java`
   - Remove three diagnostic database queries that ran before every product search and could disagree with the actual query.
   - Keep one parameterized search across product code, part number, name, and description, limited to 20 active products.
   - Remove unused imports and stale commented code.
3. `ErpAiIntentClassifierTest.java`
   - Add parameterized coverage for `matching`, `containing`, and `with` product-search phrasing.

## Reliability functionality reviewed and preserved

The uploaded source already contains and wires these capabilities; this patch does not replace them:

- Per-user rolling-window rate limiting (`AiRateLimiterService`).
- Bounded global concurrent execution permits, released in `finally` (`ErpAiAgentService`).
- HTTP 429 responses with `Retry-After` (`GlobalExceptionHandler`).
- Bounded provider retries for eligible 429/502/503/504 and connection failures, exponential backoff, jitter, `Retry-After`, elapsed-time budget, and interruption handling (`AiResilienceService`).
- Provider retry integration for Spring AI chat calls in `ErpAiGraphNodes` and `ErpAiLlmClassifier`.
- Request timeout, active execution registry, cancellation, shutdown handling, metrics, token usage and audit instrumentation.
- Existing PostgreSQL FTS RAG, approval/access filtering and citation validation are untouched.

## Apply

From the project root, apply `ai-targeted-fix.patch` using Git:

```bash
git apply ai-targeted-fix.patch
```

Alternatively, copy the three files under `src/` from this ZIP over the matching project paths.

## Verification status and scope

- `git diff --check` was run on the edited files.
- The Java/Maven test suite was **not run** in this environment: the project requires Java 25, this environment has Java 21, and Maven Wrapper could not download Maven 3.10.0.
- This is a targeted defect-fix overlay, **not** the complete Phase 1–4 Spring AI migration. The uploaded project still contains LangGraph4j orchestration. Removing it safely requires migrating `ErpAiAgentService`, graph nodes/state/workflow, and `LiveGroundedAnswerEvaluationService` together with their tests. No graph classes or dependencies were deleted because doing so here would leave unresolved production and test references.
- Existing rate limiting and retries are currently in-process/per-JVM. Horizontal scaling requires shared coordination (for example, a database-backed or Redis-backed limiter/lease store); the current implementation's own documentation calls out this limitation.
