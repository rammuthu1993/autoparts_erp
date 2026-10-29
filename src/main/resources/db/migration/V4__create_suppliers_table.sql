
CREATE TABLE suppliers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    supplier_name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(150),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(254),
    address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(100),
    pincode VARCHAR(10),
    gstin VARCHAR(15),
    payment_terms_days INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_suppliers_payment_terms
        CHECK (payment_terms_days >= 0)
);

CREATE UNIQUE INDEX uq_suppliers_gstin
    ON suppliers (UPPER(gstin))
    WHERE gstin IS NOT NULL AND TRIM(gstin) <> '';

CREATE INDEX idx_suppliers_name
    ON suppliers (LOWER(supplier_name));

CREATE INDEX idx_suppliers_active
    ON suppliers (active);

    
-- Resolve existing duplicates before applying these indexes.

CREATE UNIQUE INDEX uq_suppliers_name_lower
    ON suppliers (LOWER(TRIM(supplier_name)));

CREATE UNIQUE INDEX uq_suppliers_phone_normalized
    ON suppliers (REGEXP_REPLACE(TRIM(phone), '[[:space:]]+', '', 'g'));

CREATE UNIQUE INDEX uq_suppliers_email_lower
    ON suppliers (LOWER(TRIM(email)))
    WHERE email IS NOT NULL AND TRIM(email) <> '';

-- Replace the previous GSTIN index if it exists.
DROP INDEX IF EXISTS uq_suppliers_gstin;

CREATE UNIQUE INDEX uq_suppliers_gstin
    ON suppliers (UPPER(TRIM(gstin)))
    WHERE gstin IS NOT NULL AND TRIM(gstin) <> '';
