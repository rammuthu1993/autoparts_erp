CREATE TABLE sales_invoice_counters (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_prefix VARCHAR(25) NOT NULL UNIQUE,
    last_number BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sales_invoice_counter_last_number
        CHECK (last_number >= 0)
);

CREATE INDEX idx_sales_invoice_counters_prefix
    ON sales_invoice_counters(invoice_prefix);
