-- Enhance customers and suppliers with industry-standard master-data fields and search/filter indexes

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(30) NOT NULL DEFAULT 'RETAIL',
    ADD COLUMN IF NOT EXISTS contact_person VARCHAR(150),
    ADD COLUMN IF NOT EXISTS alternate_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS gst_registration_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS shipping_address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS credit_period_days INTEGER;

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_credit_limit,
    DROP CONSTRAINT IF EXISTS chk_customers_credit_period;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_credit_limit
        CHECK (credit_limit IS NULL OR credit_limit >= 0),
    ADD CONSTRAINT chk_customers_credit_period
        CHECK (credit_period_days IS NULL OR credit_period_days >= 0);

CREATE INDEX IF NOT EXISTS idx_customers_email_lower
    ON customers (LOWER(TRIM(email)))
    WHERE email IS NOT NULL AND TRIM(email) <> '';

CREATE INDEX IF NOT EXISTS idx_customers_gstin_upper
    ON customers (UPPER(TRIM(gstin)))
    WHERE gstin IS NOT NULL AND TRIM(gstin) <> '';

CREATE INDEX IF NOT EXISTS idx_customers_city_lower
    ON customers (LOWER(city))
    WHERE city IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_customers_state_lower
    ON customers (LOWER(state))
    WHERE state IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_customers_type
    ON customers (customer_type);

CREATE SEQUENCE IF NOT EXISTS supplier_code_seq
    START WITH 1
    INCREMENT BY 1;

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS supplier_type VARCHAR(30) NOT NULL DEFAULT 'DISTRIBUTOR',
    ADD COLUMN IF NOT EXISTS alternate_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS gst_registration_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(12, 2);

UPDATE suppliers
SET supplier_code = 'SUP' || LPAD(id::TEXT, 6, '0')
WHERE supplier_code IS NULL;

SELECT setval(
    'supplier_code_seq',
    GREATEST(COALESCE((SELECT MAX(id) FROM suppliers), 0), 1),
    TRUE
);

ALTER TABLE suppliers
    DROP CONSTRAINT IF EXISTS chk_suppliers_credit_limit;

ALTER TABLE suppliers
    ADD CONSTRAINT chk_suppliers_credit_limit
        CHECK (credit_limit IS NULL OR credit_limit >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_code
    ON suppliers (supplier_code)
    WHERE supplier_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_suppliers_email_lower
    ON suppliers (LOWER(TRIM(email)))
    WHERE email IS NOT NULL AND TRIM(email) <> '';

CREATE INDEX IF NOT EXISTS idx_suppliers_city_lower
    ON suppliers (LOWER(city))
    WHERE city IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_suppliers_state_lower
    ON suppliers (LOWER(state))
    WHERE state IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_suppliers_type
    ON suppliers (supplier_type);
