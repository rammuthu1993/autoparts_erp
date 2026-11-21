# Stage 4 — Production Reliability, Security and Observability

**Status:** Core implementation was locally verified by the user (673 tests passed before the final audit-policy reconciliation). The two added audit-policy tests and production database-role verification must be run after applying this reconciliation patch.

This patch implements the Stage 4 tasks from `IMPLEMENTATION_PLAN.md`. It does not declare production readiness.

## Implemented in this patch

1. **Provider resilience**
   - Up to three retries after the initial provider attempt.
   - Retries only HTTP 429, 502, 503, 504 and connection-level failures.
   - Exponential delays of 1s, 2s and 4s with ±25% jitter.
   - Honors `Retry-After` when supplied.
   - Does not retry validation, authentication or authorization failures.
   - The existing `ai.agent.execution-timeout` remains the outer graph execution/cancellation deadline. Provider HTTP timeout remains separately configured.

2. **AI request and concurrency limiting**
   - Sliding window: 20 requests per authenticated user per 60 seconds.
   - Global active graph execution limit: 10.
   - Rejections return HTTP 429 and a `Retry-After` header.
   - The per-user window and global semaphore are in-memory and per application instance. This is suitable for a single-instance deployment. Before horizontal scaling, replace them with a shared/distributed limiter and shared execution leases.

3. **Micrometer metrics**
   - `ai.request.duration`
   - `ai.provider.duration`
   - `ai.node.duration`
   - `ai.tool.duration`
   - `ai.token.usage`
   - Additional bounded counters for retries, tool status and audit persistence failures.
   - No user ID, conversation ID, prompt, SQL text or other high-cardinality/private value is used as a metric tag.
   - Token usage is recorded when the provider returns usage metadata; missing provider usage metadata is not fabricated.

4. **Structured tool audit**
   - Every graph-dispatched tool execution reaches the audit hook, including success, authorization denial, failure and cancellation.
   - Captures tool intent, privacy-preserving parameter metadata, execution duration, row count and status.
   - Free-form search text is stored as SHA-256 plus character length; result rows are not copied into the audit record.
   - Adds `execution_duration_ms` and `rows_returned` through forward-only Flyway migration V34.
   - Existing V27/V29/V30/V32/V33 migrations are not edited.
   - Development defaults to best-effort auditing. Production defaults to fail-closed: missing execution context or an audit database write failure prevents a successful tool result from being returned. This makes the production audit requirement explicit rather than silently accepting missing audit rows.

5. **Conversation history budget**
   - Keeps the newest messages within a configurable 2,000-token-equivalent history budget.
   - The implementation uses a documented 4-UTF-8-bytes-per-token approximation, not a model-specific tokenizer.
   - Incoming chat messages are limited to 8,000 characters.

6. **Read-only AI database role**
   - `docs/sql/create-ai-readonly-role.sql` grants only the explicit table allowlist used by current AI tools and RAG.
   - `users` is restricted to column-level SELECT on `id` and `username`; it does not receive table-level SELECT.
   - The script avoids broad default privileges, because future tables must be reviewed before being added.
   - Run `docs/sql/verify-ai-tool-role.sql` as the AI reader account. Confirm no inherited roles, no write privileges, and no table-level SELECT on `users`.

## Configuration

The defaults are in `application.properties` and can be overridden by environment variables:

| Property | Default |
|---|---:|
| `app.ai.resilience.max-retries` | 3 |
| `app.ai.resilience.base-delay` | 1s |
| `app.ai.resilience.max-delay` | 4s |
| `app.ai.resilience.jitter` | 0.25 |
| `app.ai.rate-limit.requests-per-minute` | 20 |
| `app.ai.rate-limit.window-seconds` | 60 |
| `app.ai.rate-limit.max-concurrent-executions` | 10 |
| `app.ai.history.max-tokens` | 2000 |
| `app.ai.datasource.query-timeout-seconds` | 15 |
| `app.ai.datasource.max-rows` | 500 |

The custom resilience layer owns retries. Keep `spring.ai.openai.max-retries=0` to avoid nested/double retry policies.

## Apply and verify

1. Back up the current project and apply this patch over the project root. Review every changed file before accepting it.
2. Confirm `config/application-secrets.properties` is local-only and never included in source control or deployment artifacts.
3. Review the V34 SQL, then run the application against the intended development database so Flyway applies V34. Do not use Flyway clean/repair and do not edit applied migrations.
4. Provision the production AI reader role using the SQL script as a database administrator. Set a new strong password interactively with `\password autoparts_ai_reader` inside psql; do not put it in source control or command history.
5. Run the role verification SQL while connected as the AI reader account.
6. Run:

```powershell
.\mvnw.cmd clean -DskipTests compile
.\mvnw.cmd "-Dtest=AiRateLimiterServiceTest,AiResilienceServiceTest,AiConversationHistoryBudgetTest,AiToolAuditServiceTest,AiMetricsServiceTest,PostgresQueryTimeoutIntegrationTest" test
.\mvnw.cmd clean test
```

7. Verify the Actuator metrics endpoint with an authenticated administrator and verify HTTP 429 responses include `Retry-After`.
8. Test a real production-profile startup with separate application and AI reader credentials. Do not test production profile by pointing it at the development superuser account.

## Explicit limitations / remaining gates

- Rate limiting is per JVM. A multi-instance deployment requires a shared limiter before claiming cluster-wide enforcement.
- The token budget is an estimate. Exact token counts require the actual tokenizer for the configured Sarvam model.
- Provider token metrics depend on usage metadata returned by Sarvam.
- Development audit writes are best-effort; production defaults to fail-closed. Production acceptance must verify V34 is applied and audit rows are persisted for success, failure, authorization denial and cancellation.
- The SQL allowlist must be re-reviewed whenever AI tool SQL adds a table or column. `users` remains column-restricted.
- The role script does not create tenant isolation. The current schema and implementation must be assessed separately before any multi-tenant deployment.
- Stage 3C Phase 4 and the previously deferred live-provider grounded-answer quality work remain separate outstanding work; this patch does not claim to resolve them.
- The user subsequently reported a full-suite result of 673 tests passed, 0 failures, 0 errors and 0 skipped for the pre-reconciliation Stage 4 code. The two additional audit-policy tests in this reconciliation patch have not yet been run; do not claim the updated total until the user executes the commands.
