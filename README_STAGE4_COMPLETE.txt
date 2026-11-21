AUTO PARTS ERP — STAGE 4 COMPLETE RECONCILED CODE OVERLAY

Source baseline: user's uploaded autoparts-erp(20261120-170544).zip.

This ZIP contains the complete Stage 4-related source/configuration/migration/test
files from that snapshot, with the final audit-policy reconciliation applied.
It is a focused overlay for the same project, not a replacement project archive.
The ZIP entries begin directly with src/ and docs/; no enclosing project folder.

IMPORTANT PRESERVATION RULES
- Back up the working project first.
- Apply this only to the matching latest project snapshot.
- Preserve all other worktree modifications and untracked files.
- Do not run git reset, git clean, Flyway clean or Flyway repair.
- Do not edit already-applied V27/V29/V30/V32/V33 migrations.
- V34 is forward-only. If already applied, do not reapply it manually; Flyway
  should recognize it in flyway_schema_history.

STAGE 4 IMPLEMENTATION
- Reuses existing ActiveExecution registry, cancellation and timeout scheduler.
- Adds provider retry/backoff/jitter/Retry-After handling.
- Adds per-user sliding-window rate limiting and per-JVM active execution cap.
- Adds Micrometer AI metrics.
- Extends existing ai_tool_runs audit persistence with V34 duration/row-count data.
- Production audit is fail-closed by default (AI_AUDIT_FAIL_CLOSED=true).
- Adds approximate conversation history budgeting.
- Keeps Spring JDBC and the dedicated production AI read-only datasource.
- Includes PostgreSQL role provisioning and verification SQL.

VERIFICATION ON WINDOWS / JAVA 25
Run from the project root:

  java -version
  .\mvnw.cmd clean -DskipTests test-compile
  .\mvnw.cmd "-Dtest=AiRateLimiterServiceTest,AiResilienceServiceTest,AiConversationHistoryBudgetTest,AiToolAuditServiceTest,AiMetricsServiceTest,AiRateLimitExceptionHandlerTest,PostgresQueryTimeoutIntegrationTest" test
  .\mvnw.cmd clean test

The user previously reported 673 tests passing before this final reconciliation.
Two audit-policy tests have been added. Use actual Surefire output for the new
count; this assistant environment only has Java 21, while the project requires
Java 25, so no new build/test result is claimed here.

PRODUCTION ACCEPTANCE STILL REQUIRES
- Apply and verify V34 on the intended database.
- Verify audit rows for success, denial, failure and cancellation.
- Verify production fail-closed audit behaviour when persistence is unavailable.
- Provision and verify the dedicated AI reader role.
- Verify Actuator metrics and HTTP 429/Retry-After against the running app.
- Run representative provider fault-injection and concurrency/load tests.
- Use shared/distributed coordination before horizontal scaling; current limits
  are per JVM.
- Resolve the V29/V30 production migration reconciliation dependency.

Stage 3C Phase 4 and deferred Stage 3C live-provider grounded-answer evaluation
remain separate and are not marked complete by this Stage 4 bundle.
