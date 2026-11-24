-- V38: Add user email, email_verified, and user security tokens for OTP, password reset, and email verification

-- 1. Add email columns to users table
ALTER TABLE users
    ADD COLUMN email VARCHAR(254) NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Enforce case-insensitive uniqueness on email for non-null records (supporting legacy users with unknown email)
CREATE UNIQUE INDEX uq_users_email_lower
    ON users (LOWER(TRIM(email)))
    WHERE email IS NOT NULL;

-- 2. Create user_security_tokens table for OTP, password reset authorization, and email verification
CREATE TABLE user_security_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    user_id BIGINT NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    resend_available_at TIMESTAMPTZ NULL,
    consumed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_security_tokens_attempts
        CHECK (attempts >= 0),
    CONSTRAINT chk_security_tokens_max_attempts
        CHECK (max_attempts > 0),
    CONSTRAINT chk_security_tokens_purpose
        CHECK (purpose IN ('PASSWORD_RESET_OTP', 'PASSWORD_RESET_AUTHORIZATION', 'EMAIL_VERIFICATION'))
);

-- Index for querying active tokens by user and purpose
CREATE INDEX idx_user_security_tokens_user_purpose
    ON user_security_tokens (user_id, purpose)
    WHERE consumed_at IS NULL;

-- Index for verifying tokens by token hash and purpose
CREATE INDEX idx_user_security_tokens_hash_purpose
    ON user_security_tokens (token_hash, purpose)
    WHERE consumed_at IS NULL;

-- Index for cleanup of expired tokens
CREATE INDEX idx_user_security_tokens_expires_at
    ON user_security_tokens (expires_at);
