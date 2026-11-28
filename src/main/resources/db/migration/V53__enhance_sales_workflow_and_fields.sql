-- V53: Add persisted invoice metadata columns and filter indexes to sales.

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS supply_type VARCHAR(20) NOT NULL DEFAULT 'INTRA_STATE',
    ADD COLUMN IF NOT EXISTS customer_gstin VARCHAR(15),
    ADD COLUMN IF NOT EXISTS place_of_supply VARCHAR(100),
    ADD COLUMN IF NOT EXISTS billing_address TEXT,
    ADD COLUMN IF NOT EXISTS shipping_address TEXT,
    ADD COLUMN IF NOT EXISTS customer_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_terms VARCHAR(100),
    ADD COLUMN IF NOT EXISTS due_date DATE;

CREATE INDEX IF NOT EXISTS idx_sales_customer_id
    ON sales(customer_id);

CREATE INDEX IF NOT EXISTS idx_sales_sale_date_status
    ON sales(sale_date, status);
