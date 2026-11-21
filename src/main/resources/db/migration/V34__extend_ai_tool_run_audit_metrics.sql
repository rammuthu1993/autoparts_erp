-- Stage 4: enrich existing AI tool execution audit rows.
-- Forward-only migration. Do not edit or repair already-applied V27/V29/V30/V32/V33 migrations.

ALTER TABLE ai_tool_runs
    ADD COLUMN IF NOT EXISTS execution_duration_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rows_returned INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ai_tool_runs
    DROP CONSTRAINT IF EXISTS ai_tool_runs_status_check;

ALTER TABLE ai_tool_runs
    ADD CONSTRAINT ai_tool_runs_status_check
    CHECK (status IN ('SUCCESS', 'FAILED', 'CANCELLED'));

ALTER TABLE ai_tool_runs
    ADD CONSTRAINT ai_tool_runs_execution_duration_nonnegative
    CHECK (execution_duration_ms >= 0);

ALTER TABLE ai_tool_runs
    ADD CONSTRAINT ai_tool_runs_rows_returned_nonnegative
    CHECK (rows_returned >= 0);

CREATE INDEX IF NOT EXISTS idx_ai_tool_runs_user_created
    ON ai_tool_runs(user_subject, created_at DESC);
