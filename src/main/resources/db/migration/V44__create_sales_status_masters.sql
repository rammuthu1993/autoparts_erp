CREATE TABLE sales_statuses (
    code VARCHAR(20) PRIMARY KEY,
    label VARCHAR(50) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sales_payment_statuses (
    code VARCHAR(30) PRIMARY KEY,
    label VARCHAR(50) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sales_statuses (code, label, sort_order) VALUES
    ('DRAFT', 'Draft', 10),
    ('PENDING', 'Pending', 20),
    ('COMPLETED', 'Completed', 30),
    ('CANCELLED', 'Cancelled', 40);

INSERT INTO sales_payment_statuses (code, label, sort_order) VALUES
    ('UNPAID', 'Pending', 10),
    ('PARTIALLY_PAID', 'Partially paid', 20),
    ('PAID', 'Complete', 30);

ALTER TABLE sales DROP CONSTRAINT IF EXISTS chk_sales_status;
ALTER TABLE sales DROP CONSTRAINT IF EXISTS chk_sales_payment_status;

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_status_code
        FOREIGN KEY (status) REFERENCES sales_statuses(code),
    ADD CONSTRAINT fk_sales_payment_status_code
        FOREIGN KEY (payment_status) REFERENCES sales_payment_statuses(code);

CREATE INDEX IF NOT EXISTS idx_sales_statuses_active_order
    ON sales_statuses(active, sort_order);
CREATE INDEX IF NOT EXISTS idx_sales_payment_statuses_active_order
    ON sales_payment_statuses(active, sort_order);
