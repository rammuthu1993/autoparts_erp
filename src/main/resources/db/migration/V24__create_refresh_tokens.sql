
CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id BIGINT NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,

    token_hash CHAR(64) NOT NULL UNIQUE,

    token_family UUID NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    revoked_at TIMESTAMPTZ NULL,

    replaced_by_token_hash CHAR(64) NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_refresh_token_hash_length
        CHECK (LENGTH(token_hash) = 64),

    CONSTRAINT chk_refresh_replacement_hash_length
        CHECK (
            replaced_by_token_hash IS NULL
            OR LENGTH(replaced_by_token_hash) = 64
        )
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_family
    ON refresh_tokens(token_family);

CREATE INDEX idx_refresh_tokens_active
    ON refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;
