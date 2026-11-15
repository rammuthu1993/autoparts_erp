CREATE TABLE ai_agent_runs (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL
        REFERENCES ai_conversations(id),
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);