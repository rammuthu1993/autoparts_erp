
-- =========================================================
-- SALES
-- =========================================================

CREATE TABLE sales (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    invoice_number VARCHAR(50) NOT NULL UNIQUE,

    customer_name VARCHAR(200),
    customer_phone VARCHAR(20),

    sale_date DATE NOT NULL DEFAULT CURRENT_DATE,

    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    cgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    total_tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    balance_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',

    notes VARCHAR(1000),

    created_by BIGINT REFERENCES users(id),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sales_status
        CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED')),

    CONSTRAINT chk_sales_payment_status
        CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')),

    CONSTRAINT chk_sales_amounts
        CHECK (
            subtotal >= 0
            AND discount_amount >= 0
            AND taxable_amount >= 0
            AND cgst_amount >= 0
            AND sgst_amount >= 0
            AND igst_amount >= 0
            AND total_tax_amount >= 0
            AND grand_total >= 0
            AND paid_amount >= 0
            AND balance_amount >= 0
        )
);

CREATE INDEX idx_sales_sale_date
    ON sales(sale_date);

CREATE INDEX idx_sales_status
    ON sales(status);

CREATE INDEX idx_sales_payment_status
    ON sales(payment_status);


-- =========================================================
-- SALES ITEMS
-- =========================================================

CREATE TABLE sales_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    sale_id BIGINT NOT NULL
        REFERENCES sales(id) ON DELETE RESTRICT,

    product_id BIGINT NOT NULL
        REFERENCES products(id) ON DELETE RESTRICT,

    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,

    quantity NUMERIC(14,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,

    unit_price NUMERIC(14,2) NOT NULL,

    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    taxable_amount NUMERIC(14,2) NOT NULL,

    hsn_code VARCHAR(20),

    gst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    cgst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    sgst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    igst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,

    cgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    total_tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(14,2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sales_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_sales_item_amounts
        CHECK (
            unit_price >= 0
            AND discount_amount >= 0
            AND taxable_amount >= 0
            AND line_total >= 0
        )
);

CREATE INDEX idx_sales_items_sale_id
    ON sales_items(sale_id);

CREATE INDEX idx_sales_items_product_id
    ON sales_items(product_id);


-- =========================================================
-- SALES PAYMENTS
-- =========================================================

CREATE TABLE sales_payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    sale_id BIGINT NOT NULL
        REFERENCES sales(id) ON DELETE RESTRICT,

    payment_method VARCHAR(20) NOT NULL,

    amount NUMERIC(14,2) NOT NULL,

    reference_number VARCHAR(100),

    payment_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    notes VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sales_payment_method
        CHECK (
            payment_method IN (
                'CASH',
                'UPI',
                'CARD',
                'BANK',
                'CREDIT'
            )
        ),

    CONSTRAINT chk_sales_payment_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_sales_payments_sale_id
    ON sales_payments(sale_id);

CREATE INDEX idx_sales_payments_payment_date
    ON sales_payments(payment_date);
