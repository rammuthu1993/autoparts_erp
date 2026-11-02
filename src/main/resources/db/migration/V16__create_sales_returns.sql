
-- =========================================================
-- SALES RETURNS
-- =========================================================

CREATE TABLE sales_returns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    sale_id BIGINT NOT NULL
        REFERENCES sales(id) ON DELETE RESTRICT,

    return_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    reason VARCHAR(500) NOT NULL,
    notes VARCHAR(1000),

    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    cgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    total_tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_return_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    status VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sales_return_status
        CHECK (status IN (
            'ACCEPTED',
            'PARTIALLY_REFUNDED',
            'REFUNDED'
        )),

    CONSTRAINT chk_sales_return_amounts
        CHECK (
            subtotal >= 0
            AND discount_amount >= 0
            AND taxable_amount >= 0
            AND cgst_amount >= 0
            AND sgst_amount >= 0
            AND igst_amount >= 0
            AND total_tax_amount >= 0
            AND total_return_amount >= 0
        )
);

CREATE INDEX idx_sales_returns_sale_id
    ON sales_returns(sale_id);

CREATE INDEX idx_sales_returns_date
    ON sales_returns(return_date);


-- =========================================================
-- SALES RETURN PRODUCTS
-- =========================================================

CREATE TABLE sales_return_products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    sales_return_id BIGINT NOT NULL
        REFERENCES sales_returns(id) ON DELETE RESTRICT,

    sales_product_id BIGINT NOT NULL
        REFERENCES sales_products(id) ON DELETE RESTRICT,

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

    CONSTRAINT chk_sales_return_product_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_sales_return_product_amounts
        CHECK (
            unit_price >= 0
            AND discount_amount >= 0
            AND taxable_amount >= 0
            AND line_total >= 0
        )
);

CREATE INDEX idx_sales_return_products_return_id
    ON sales_return_products(sales_return_id);

CREATE INDEX idx_sales_return_products_sales_product_id
    ON sales_return_products(sales_product_id);

CREATE INDEX idx_sales_return_products_product_id
    ON sales_return_products(product_id);


-- =========================================================
-- SALES REFUNDS
-- =========================================================

CREATE TABLE sales_refunds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    sales_return_id BIGINT NOT NULL
        REFERENCES sales_returns(id) ON DELETE RESTRICT,

    payment_method VARCHAR(20) NOT NULL,

    amount NUMERIC(14,2) NOT NULL,

    reference_number VARCHAR(100),
    refund_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sales_refund_amount
        CHECK (amount > 0),

    CONSTRAINT chk_sales_refund_payment_method
        CHECK (payment_method IN (
            'CASH',
            'UPI',
            'CARD',
            'BANK',
            'CREDIT'
        ))
);

CREATE INDEX idx_sales_refunds_return_id
    ON sales_refunds(sales_return_id);

CREATE INDEX idx_sales_refunds_date
    ON sales_refunds(refund_date);
