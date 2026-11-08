
CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    user_id BIGINT NULL,

    username VARCHAR(100) NOT NULL,

    action VARCHAR(50) NOT NULL,

    entity_type VARCHAR(100) NOT NULL,

    entity_id VARCHAR(100),

    description VARCHAR(500),

    old_values JSONB,

    new_values JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_audit_logs_action
        CHECK (
            action IN (
                'CREATE',
                'UPDATE',
                'DELETE',
                'CANCEL',
                'APPROVE',
                'REFUND',
                'ADJUST'
            )
        )
);

CREATE INDEX idx_audit_logs_user_id
    ON audit_logs(user_id);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id);

CREATE INDEX idx_audit_logs_action
    ON audit_logs(action);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs(created_at DESC);
