
CREATE SEQUENCE purchase_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE purchases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    purchase_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    supplier_invoice_no VARCHAR(100) NOT NULL,
    purchase_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    subtotal NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(14, 2) NOT NULL DEFAULT 0,

    notes VARCHAR(1000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at TIMESTAMPTZ,

    CONSTRAINT fk_purchases_supplier
        FOREIGN KEY (supplier_id)
        REFERENCES suppliers(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_purchase_supplier_invoice
        UNIQUE (supplier_id, supplier_invoice_no),

    CONSTRAINT chk_purchase_status
        CHECK (status IN ('DRAFT', 'RECEIVED', 'CANCELLED')),

    CONSTRAINT chk_purchase_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_purchase_discount
        CHECK (discount_total >= 0),

    CONSTRAINT chk_purchase_tax
        CHECK (tax_total >= 0),

    CONSTRAINT chk_purchase_grand_total
        CHECK (grand_total >= 0),

    CONSTRAINT chk_purchase_received_at
        CHECK (
            (status = 'RECEIVED' AND received_at IS NOT NULL)
            OR
            (status <> 'RECEIVED' AND received_at IS NULL)
        )
);

-- Treat invoice numbers as case-insensitive and ignore
-- leading/trailing spaces for duplicate detection.
CREATE UNIQUE INDEX uq_purchase_supplier_invoice_normalized
    ON purchases (
        supplier_id,
        LOWER(TRIM(supplier_invoice_no))
    );

CREATE INDEX idx_purchases_supplier_id
    ON purchases(supplier_id);

CREATE INDEX idx_purchases_purchase_date
    ON purchases(purchase_date);

CREATE INDEX idx_purchases_status
    ON purchases(status);


CREATE TABLE purchase_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    purchase_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    quantity NUMERIC(12, 3) NOT NULL,
    purchase_price NUMERIC(12, 2) NOT NULL,
    discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    line_total NUMERIC(14, 2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_items_purchase
        FOREIGN KEY (purchase_id)
        REFERENCES purchases(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_purchase_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    -- A product can appear only once per invoice.
    CONSTRAINT uq_purchase_item_product
        UNIQUE (purchase_id, product_id),

    CONSTRAINT chk_purchase_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_purchase_item_price
        CHECK (purchase_price >= 0),

    CONSTRAINT chk_purchase_item_discount
        CHECK (discount_amount >= 0),

    CONSTRAINT chk_purchase_item_tax_percent
        CHECK (tax_percent >= 0 AND tax_percent <= 100),

    CONSTRAINT chk_purchase_item_tax_amount
        CHECK (tax_amount >= 0),

    CONSTRAINT chk_purchase_item_line_total
        CHECK (line_total >= 0)
);

CREATE INDEX idx_purchase_items_product_id
    ON purchase_items(product_id);
