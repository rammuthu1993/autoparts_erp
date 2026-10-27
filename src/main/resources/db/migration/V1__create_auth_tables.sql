
-- 1. USERS
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Case-insensitive username uniqueness
CREATE UNIQUE INDEX uq_users_username_lower
    ON users (LOWER(username));


-- 2. ROLES
CREATE TABLE roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- 3. USER_ROLES
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,

    role_id BIGINT NOT NULL
        REFERENCES roles(id) ON DELETE RESTRICT,

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id
    ON user_roles (role_id);


-- 4. INITIAL ROLES
INSERT INTO roles (uuid, name, description)
VALUES
    ('11111111-1111-4111-8111-111111111111',
     'ADMIN', 'Full ERP administration access'),

    ('22222222-2222-4222-8222-222222222222',
     'MANAGER', 'ERP management access'),

    ('33333333-3333-4333-8333-333333333333',
     'CASHIER', 'Sales and billing access')
ON CONFLICT (name) DO NOTHING;
