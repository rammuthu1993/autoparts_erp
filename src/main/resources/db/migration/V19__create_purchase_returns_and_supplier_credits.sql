
-- =========================================================
-- PURCHASE RETURN NUMBER SEQUENCE
-- =========================================================

CREATE SEQUENCE purchase_return_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;


-- =========================================================
-- PURCHASE RETURNS
-- =========================================================

CREATE TABLE purchase_returns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    return_number VARCHAR(30) NOT NULL UNIQUE,

    purchase_id BIGINT NOT NULL
        REFERENCES purchases(id) ON DELETE RESTRICT,

    return_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    reason VARCHAR(500) NOT NULL,
    notes VARCHAR(1000),

    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_return_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_purchase_return_amounts
        CHECK (
            subtotal >= 0
            AND discount_total >= 0
            AND tax_total >= 0
            AND total_return_amount >= 0
        )
);

CREATE INDEX idx_purchase_returns_purchase_id
    ON purchase_returns(purchase_id);

CREATE INDEX idx_purchase_returns_date
    ON purchase_returns(return_date);


-- =========================================================
-- PURCHASE RETURN PRODUCTS
-- =========================================================

CREATE TABLE purchase_return_products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    purchase_return_id BIGINT NOT NULL
        REFERENCES purchase_returns(id) ON DELETE RESTRICT,

    purchase_product_id BIGINT NOT NULL
        REFERENCES purchase_products(id) ON DELETE RESTRICT,

    product_id BIGINT NOT NULL
        REFERENCES products(id) ON DELETE RESTRICT,

    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,

    quantity NUMERIC(12,3) NOT NULL,

    purchase_price NUMERIC(12,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    tax_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    line_total NUMERIC(14,2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_purchase_return_product_line
        UNIQUE (purchase_return_id, purchase_product_id),

    CONSTRAINT chk_purchase_return_product_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_purchase_return_product_amounts
        CHECK (
            purchase_price >= 0
            AND discount_amount >= 0
            AND tax_percent >= 0
            AND tax_percent <= 100
            AND tax_amount >= 0
            AND line_total >= 0
        )
);

CREATE INDEX idx_purchase_return_products_return_id
    ON purchase_return_products(purchase_return_id);

CREATE INDEX idx_purchase_return_products_purchase_product_id
    ON purchase_return_products(purchase_product_id);

CREATE INDEX idx_purchase_return_products_product_id
    ON purchase_return_products(product_id);


-- =========================================================
-- SUPPLIER CREDITS
-- One credit record is created for each purchase return.
-- =========================================================

CREATE TABLE supplier_credits (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    purchase_return_id BIGINT NOT NULL UNIQUE
        REFERENCES purchase_returns(id) ON DELETE RESTRICT,

    credit_note_number VARCHAR(100),

    credit_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    amount NUMERIC(14,2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_supplier_credit_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_supplier_credits_credit_date
    ON supplier_credits(credit_date);


-- =========================================================
-- SUPPLIER CREDIT APPLICATIONS
-- Apply supplier credit against future purchase invoices.
-- =========================================================

CREATE TABLE supplier_credit_applications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    supplier_credit_id BIGINT NOT NULL
        REFERENCES supplier_credits(id) ON DELETE RESTRICT,

    purchase_id BIGINT NOT NULL
        REFERENCES purchases(id) ON DELETE RESTRICT,

    amount NUMERIC(14,2) NOT NULL,

    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by BIGINT NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_supplier_credit_purchase
        UNIQUE (supplier_credit_id, purchase_id),

    CONSTRAINT chk_supplier_credit_application_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_supplier_credit_applications_credit_id
    ON supplier_credit_applications(supplier_credit_id);

CREATE INDEX idx_supplier_credit_applications_purchase_id
    ON supplier_credit_applications(purchase_id);


-- =========================================================
-- SUPPLIER REFUNDS
-- Actual money received from suppliers.
-- =========================================================

CREATE TABLE supplier_refunds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    supplier_credit_id BIGINT NOT NULL
        REFERENCES supplier_credits(id) ON DELETE RESTRICT,

    payment_method VARCHAR(20) NOT NULL,

    amount NUMERIC(14,2) NOT NULL,

    reference_number VARCHAR(100),

    refund_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    notes VARCHAR(500),

    created_by BIGINT NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_supplier_refund_amount
        CHECK (amount > 0),

    CONSTRAINT chk_supplier_refund_payment_method
        CHECK (
            payment_method IN ('CASH', 'UPI', 'CARD', 'BANK')
        )
);

CREATE INDEX idx_supplier_refunds_credit_id
    ON supplier_refunds(supplier_credit_id);

CREATE INDEX idx_supplier_refunds_date
    ON supplier_refunds(refund_date);


-- =========================================================
-- CASH REGISTER INTEGRATION
-- Allow supplier refunds as a cash register source.
-- =========================================================

ALTER TABLE cash_register_movements
    DROP CONSTRAINT chk_cash_movement_source;

ALTER TABLE cash_register_movements
    ADD CONSTRAINT chk_cash_movement_source
    CHECK (
        source_type IN (
            'MANUAL',
            'SALE_PAYMENT',
            'PURCHASE_PAYMENT',
            'EXPENSE',
            'SALES_REFUND',
            'SUPPLIER_REFUND',
            'CUSTOMER_COLLECTION',
            'ADJUSTMENT'
        )
    );
