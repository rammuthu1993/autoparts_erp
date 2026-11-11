CREATE INDEX IF NOT EXISTS idx_ai_conversations_user
ON ai_conversations (user_subject, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_messages_conversation
ON ai_messages (conversation_id, created_at, id);