-- ====================================================================
-- Auto Parts ERP AI Assistant: AI Agent Runs (Stage 3A)
-- Flyway Migration V30
--
-- Safe migration correction:
-- Idempotent schema alignment for ai_agent_runs.
-- In fresh databases: evolves the V29 base table into the complete V30 structure.
-- In existing databases: safely preserves existing table, schema, and rows.
-- ====================================================================

DO $$
BEGIN
    -- If the table does not exist at all, create it with the full schema
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'ai_agent_runs'
    ) THEN
        CREATE TABLE ai_agent_runs (
            id UUID PRIMARY KEY,
            conversation_id UUID NOT NULL,
            user_subject VARCHAR(150) NOT NULL,
            status VARCHAR(30) NOT NULL,
            attempt_count INTEGER NOT NULL DEFAULT 1,
            error_message TEXT,
            started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            completed_at TIMESTAMPTZ,
            CONSTRAINT fk_ai_agent_runs_conversation
                FOREIGN KEY (conversation_id)
                REFERENCES ai_conversations(id),
            CONSTRAINT chk_ai_agent_runs_status
                CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
            CONSTRAINT chk_ai_agent_runs_attempt_count
                CHECK (attempt_count >= 1)
        );

        CREATE INDEX idx_ai_agent_runs_conversation ON ai_agent_runs(conversation_id);
        CREATE INDEX idx_ai_agent_runs_user ON ai_agent_runs(user_subject);
        CREATE INDEX idx_ai_agent_runs_status ON ai_agent_runs(status);

    ELSE
        -- Table exists from V29. Upgrade it to the complete V30 schema.
        -- 1. Add user_subject if missing
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'ai_agent_runs' AND column_name = 'user_subject'
        ) THEN
            ALTER TABLE ai_agent_runs ADD COLUMN user_subject VARCHAR(150) NOT NULL DEFAULT 'SYSTEM';
            ALTER TABLE ai_agent_runs ALTER COLUMN user_subject DROP DEFAULT;
        END IF;

        -- 2. Adjust attempt_count default and backfill values < 1
        UPDATE ai_agent_runs SET attempt_count = 1 WHERE attempt_count < 1;
        ALTER TABLE ai_agent_runs ALTER COLUMN attempt_count SET DEFAULT 1;

        -- 3. Add check constraints if missing
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conrelid = 'public.ai_agent_runs'::regclass AND conname = 'chk_ai_agent_runs_status'
        ) THEN
            ALTER TABLE ai_agent_runs ADD CONSTRAINT chk_ai_agent_runs_status
                CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conrelid = 'public.ai_agent_runs'::regclass AND conname = 'chk_ai_agent_runs_attempt_count'
        ) THEN
            ALTER TABLE ai_agent_runs ADD CONSTRAINT chk_ai_agent_runs_attempt_count
                CHECK (attempt_count >= 1);
        END IF;

        -- 4. Create indexes if missing
        CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_conversation ON ai_agent_runs(conversation_id);
        CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_user ON ai_agent_runs(user_subject);
        CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_status ON ai_agent_runs(status);
    END IF;
END $$;
