
-- Add failed login tracking and temporary account locking.
-- Existing users remain unlocked by default.

ALTER TABLE users
ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
ADD COLUMN locked_until TIMESTAMPTZ NULL;

ALTER TABLE users
ADD CONSTRAINT chk_users_failed_login_attempts
CHECK (failed_login_attempts >= 0);
