# Phase 4 Production Readiness — Reconciled Implementation

## Scope

This document describes the Stage 4 implementation for AI reliability, rate limiting, observability, tool auditing, query safeguards and production AI database isolation. It does not certify the whole ERP as production-ready.

## Existing features reused (not rebuilt)

- `ErpAiAgentService.ActiveExecution`, its `ConcurrentHashMap` registry, timeout scheduler, cancellation and shutdown handling.
- Existing `ai.agent.execution-timeout` outer graph deadline.
- Existing `ai_tool_runs` table and `AiRepository.saveToolRun(...)` repository operation.
- Existing Spring Boot Actuator dependency and application security.
- Existing immutable `ErpAiExecutionContext` and backend role checks.
- Existing JDBC-based AI tool query path.

## Stage 4 additions and extensions

- `AiResilienceService`: bounded retries for HTTP 429/502/503/504 and transient connection errors, exponential backoff, jitter and `Retry-After` support.
- `AiRateLimiterService`: per-user sliding-window limit and an in-process semaphore for active graph executions.
- `AiMetricsService`: request, provider, node, tool, token, retry and audit-failure metrics with bounded tags.
- `AiToolAuditService`: structured audit metadata, duration and returned-row count; production fail-closed policy is controlled by `app.ai.audit.fail-closed` and defaults to `true` in `prod`.
- `AiConversationHistoryBudget`: configurable approximate history budget; byte-based estimation is explicitly not an exact Sarvam tokenizer.
- V34: forward-only extension of `ai_tool_runs`; do not edit or repair already-applied migrations.
- AI JDBC safeguards: configurable query timeout and maximum returned rows.
- Production-only AI reader datasource using separate PostgreSQL credentials.

## Required production environment variables

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://HOST:5432/autoparts_erp
DB_USERNAME=APPLICATION_DATABASE_ROLE
DB_PASSWORD=APPLICATION_DATABASE_PASSWORD
JWT_SECRET=BASE64_ENCODED_RANDOM_SECRET_AT_LEAST_32_BYTES
SARVAM_API_KEY=PROVIDER_API_KEY
AI_TOOL_DB_URL=jdbc:postgresql://HOST:5432/autoparts_erp
AI_TOOL_DB_USERNAME=autoparts_ai_reader
AI_TOOL_DB_PASSWORD=STRONG_READ_ONLY_ROLE_PASSWORD
APP_CORS_ALLOWED_ORIGINS=https://your-approved-frontend.example
AI_AUDIT_FAIL_CLOSED=true
```

Do not commit secrets. Rotate credentials that were previously exposed in source archives or Git history.

## Provision and verify the AI reader role

1. Inspect `docs/sql/create-ai-readonly-role.sql` and replace the owner-role placeholder only if the script requires it for your environment.
2. Run the provisioning script as a PostgreSQL administrator.
3. In the interactive `psql` session, set the password using `\password autoparts_ai_reader`.
4. Connect as `autoparts_ai_reader` to the target database.
5. Run `docs/sql/verify-ai-tool-role.sql`. It fails if the role is elevated, inherits another role, has public-schema CREATE, has table write grants, or has broad/sensitive `users` SELECT access.
6. Review the explicit table allowlist whenever an AI tool adds a new table or column. The script intentionally does not grant SELECT on all tables or future tables.

The database grants are the security boundary. Hikari's read-only setting is defense in depth, not a replacement for PostgreSQL permissions.

## Migration safety

- V34 is forward-only and extends `ai_tool_runs`.
- Do not modify V27, V29, V30, V32 or V33.
- Do not run Flyway `clean` or `repair` to force the migration through.
- Before deployment, inspect `flyway_schema_history` and resolve the known V29/V30 reconciliation dependency on the target database. Do not assume a fresh production database can be migrated until that dependency is resolved.

## Verification commands (Windows PowerShell)

Run from the project root using the configured Java 25 installation:

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean -DskipTests test-compile
.\mvnw.cmd "-Dtest=AiRateLimiterServiceTest,AiResilienceServiceTest,AiConversationHistoryBudgetTest,AiToolAuditServiceTest,AiMetricsServiceTest,AiRateLimitExceptionHandlerTest,PostgresQueryTimeoutIntegrationTest" test
.\mvnw.cmd clean test
```

The user previously reported 673 tests passing before the final audit-policy reconciliation. The reconciliation adds two audit-policy tests; the updated test total must be taken from the new local Surefire output, not inferred here.

After automated tests:

- Confirm V34 is present in the target development database's Flyway history.
- Trigger a successful tool call, a denied tool call and a cancelled/failed tool call; verify corresponding `ai_tool_runs` rows, statuses, duration and row counts.
- Temporarily make audit persistence unavailable in a safe test environment and confirm production fail-closed behaviour prevents a successful tool result.
- Verify `/actuator/metrics` using an authenticated authorized account and inspect `ai.request.duration`, `ai.provider.duration`, `ai.node.duration`, `ai.tool.duration`, `ai.token.usage`, retry counters and audit-failure counters.
- Verify HTTP 429 and `Retry-After`.
- Verify the `prod` profile uses the dedicated AI reader datasource, not the application datasource, for AI lookup queries.
- Run concurrency and provider fault-injection tests in a representative non-production environment.

## Known limitations and gates

- Rate limiting and the 10-execution semaphore are per JVM. They do not provide a cluster-wide limit across multiple application instances. Add shared/distributed coordination before horizontal scaling.
- History budgeting uses a four-UTF-8-bytes-per-token heuristic, not a Sarvam tokenizer. Provider usage metadata is the source for actual token reporting when available.
- Provider token metrics cannot be guaranteed if Sarvam omits usage metadata.
- The AI reader allowlist must be reviewed when SQL access changes. The current script does not implement tenant isolation.
- SLA targets require representative load measurements; passing unit tests alone does not prove them.
- Stage 3C Phase 4 and the deferred Stage 3C live grounded-answer evaluation remain separate from Stage 4 and are not marked complete here.
