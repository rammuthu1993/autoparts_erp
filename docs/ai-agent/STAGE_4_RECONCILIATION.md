# Stage 4 Reconciliation — Reuse, Extend, Implement

This is the consolidated reconciliation against the five-stage implementation plan. It prevents rebuilding features that already existed before Stage 4.

## Reuse — already present before Stage 4

| Existing component | Stage 4 treatment |
|---|---|
| `ErpAiAgentService.ActiveExecution` and `ConcurrentHashMap` registry | Reuse; do not add a second execution registry |
| Existing timeout scheduler, cancellation and shutdown handling | Reuse as the graph execution lifecycle |
| `ai.agent.execution-timeout` | Reuse as the outer execution deadline |
| `ai_tool_runs` table from V27 | Reuse; do not create a duplicate audit table |
| `AiRepository.saveToolRun(...)` | Extend the existing repository operation |
| Existing Actuator dependency and Spring Security | Configure and instrument; do not add another monitoring stack |
| `ErpAiExecutionContext` and backend role authorization | Preserve as the authorization source of truth |
| Existing Spring JDBC AI lookup path | Reuse; do not introduce JPA/Hibernate |

## Extend — existing functionality enhanced by Stage 4

| Component | Extension |
|---|---|
| AI execution lifecycle | Add a global in-process semaphore while retaining the existing per-conversation registry and cancellation lifecycle |
| Tool audit persistence | Add structured metadata, execution duration, row count and production fail-closed policy |
| `ai_tool_runs` schema | V34 adds `execution_duration_ms`, `rows_returned`, status constraints and lookup index |
| AI JDBC template | Configurable query timeout and maximum rows |
| Actuator | Expose authenticated metrics and instrument AI request/provider/node/tool/token activity |
| Conversation history | Apply configurable approximate history budget before graph invocation |

## Implement — genuinely new Stage 4 services

- `AiResilienceService`
- `AiRateLimiterService`
- `AiMetricsService`
- `AiToolAuditService`
- `AiConversationHistoryBudget`
- `AiToolAuditPersistenceException`
- Dedicated production AI reader datasource and least-privilege SQL provisioning/verification scripts

## Acceptance evidence

The user reported the pre-reconciliation full suite as 673 tests passed, zero failures, zero errors and zero skipped. The current reconciliation adds two audit-policy tests. The updated full-suite count is intentionally not stated until the tests are executed on the user's Java 25 Windows environment.

Production acceptance remains separate from automated tests and requires:

1. V34 successfully applied and verified in the intended database.
2. Audit rows verified for success, authorization denial, failure and cancellation.
3. Production fail-closed behaviour verified when audit persistence is unavailable.
4. Dedicated AI reader login verified with `docs/sql/verify-ai-tool-role.sql`.
5. Actuator AI metrics and HTTP 429/`Retry-After` verified through the running application.
6. Provider retry, timeout, cancellation and concurrent load verified in a non-production environment.
7. Multi-instance deployments must add shared rate-limit/concurrency coordination; current limits are per JVM.

## Safety constraints

- Do not reset, clean or overwrite the user's existing worktree.
- Do not modify already-applied V27/V29/V30/V32/V33 migrations.
- Do not run Flyway `clean` or `repair` to force migration success.
- Resolve the documented V29/V30 production reconciliation dependency before fresh production deployment.
- Stage 3C Phase 4 and the deferred Stage 3C live-provider evaluation are separate work and are not marked complete by Stage 4.
