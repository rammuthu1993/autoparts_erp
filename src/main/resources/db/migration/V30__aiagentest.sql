CREATE TABLE ai_agent_runs (
    id UUID PRIMARY KEY,

    conversation_id UUID NOT NULL,

    user_subject VARCHAR(150) NOT NULL,

    status VARCHAR(30) NOT NULL,

    attempt_count INTEGER NOT NULL DEFAULT 1,

    error_message TEXT,

    started_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_ai_agent_runs_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations(id),

    CONSTRAINT chk_ai_agent_runs_status
        CHECK (
            status IN (
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_ai_agent_runs_attempt_count
        CHECK (attempt_count >= 1)
);

CREATE INDEX idx_ai_agent_runs_conversation
ON ai_agent_runs(conversation_id);

CREATE INDEX idx_ai_agent_runs_user
ON ai_agent_runs(user_subject);

CREATE INDEX idx_ai_agent_runs_status
ON ai_agent_runs(status);