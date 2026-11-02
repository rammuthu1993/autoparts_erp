-- V13__create_expense_tables.sql

CREATE TABLE expense_categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_expense_categories_name_lower
    ON expense_categories (LOWER(name));

CREATE TABLE expenses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    category_id BIGINT NOT NULL,
    expense_date DATE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    amount NUMERIC(14, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    reference_number VARCHAR(100),
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMPTZ,

    CONSTRAINT fk_expenses_category
        FOREIGN KEY (category_id)
        REFERENCES expense_categories(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_expenses_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_expenses_amount
        CHECK (amount > 0),

    CONSTRAINT chk_expenses_payment_method
        CHECK (payment_method IN ('CASH', 'UPI', 'CARD', 'BANK')),

    CONSTRAINT chk_expenses_status
        CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE INDEX idx_expenses_expense_date
    ON expenses (expense_date DESC);

CREATE INDEX idx_expenses_category_id
    ON expenses (category_id);

CREATE INDEX idx_expenses_status_date
    ON expenses (status, expense_date DESC);

CREATE INDEX idx_expenses_created_by
    ON expenses (created_by);