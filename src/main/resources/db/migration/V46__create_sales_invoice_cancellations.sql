-- =============================================================================
-- Migration: V46__create_sales_invoice_cancellations.sql
-- Description: Full-invoice cancellation history, item stock restoration tracking,
--              cancellation refund lifecycle, GST/IRN metadata, and permissions.
-- =============================================================================

-- 1. Add SALES.CANCEL_ISSUED permission and grant to ADMIN and MANAGER roles
INSERT INTO permissions (code, name, description, module, action)
VALUES (
    'SALES.CANCEL_ISSUED',
    'Cancel Issued Sales Invoices',
    'Cancel already issued sales invoices with stock, receivable, refund, and GST reversal',
    'SALES',
    'CANCEL_ISSUED'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'SALES.CANCEL_ISSUED'
WHERE r.name IN ('ADMIN', 'MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2. Extend inventory_movements check constraints for issued invoice cancellation
ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_movement_type;
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movement_type
        CHECK (movement_type IN (
            'PURCHASE_RECEIPT',
            'SALE',
            'SALES_RETURN',
            'SALES_CANCELLATION',
            'PURCHASE_RETURN',
            'ADJUSTMENT',
            'OPENING_STOCK'
        ));

ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_reference_type;
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_reference_type
        CHECK (reference_type IN (
            'PURCHASE',
            'SALE',
            'SALES_RETURN',
            'SALE_CANCELLATION',
            'PURCHASE_RETURN',
            'ADJUSTMENT',
            'OPENING_STOCK'
        ));

-- 3. Extend cash_register_movements check constraint for cancellation refunds
ALTER TABLE cash_register_movements DROP CONSTRAINT IF EXISTS chk_cash_movement_source;
ALTER TABLE cash_register_movements
    ADD CONSTRAINT chk_cash_movement_source
        CHECK (
            source_type IN (
                'MANUAL',
                'SALE_PAYMENT',
                'PURCHASE_PAYMENT',
                'EXPENSE',
                'SALES_REFUND',
                'SALES_CANCELLATION_REFUND',
                'CUSTOMER_COLLECTION',
                'ADJUSTMENT',
                'SUPPLIER_REFUND'
            )
        );

-- 4. Create sales_invoice_cancellations table (one record per cancelled issued invoice)
CREATE TABLE IF NOT EXISTS sales_invoice_cancellations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    sale_id BIGINT NOT NULL UNIQUE
        REFERENCES sales(id) ON DELETE RESTRICT,
    invoice_number VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    cancellation_reason VARCHAR(500) NOT NULL,
    cancelled_by BIGINT NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,
    cancelled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    original_grand_total NUMERIC(14,2) NOT NULL,
    original_taxable_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_cgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_sgst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_igst_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_total_tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    previously_paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    previously_returned_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    previously_refunded_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    cancelled_invoice_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    refund_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    refund_method VARCHAR(20),
    refund_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED',
    refund_reference VARCHAR(100),
    refund_confirmed_at TIMESTAMPTZ,
    refund_confirmed_by BIGINT
        REFERENCES users(id) ON DELETE RESTRICT,

    receivable_adjustment_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    gst_adjustment_type VARCHAR(40) NOT NULL DEFAULT 'INVOICE_CANCELLATION',
    gst_adjustment_reference VARCHAR(100),
    credit_note_number VARCHAR(50),
    irn VARCHAR(100),
    irn_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPLICABLE',
    eway_bill_number VARCHAR(50),
    eway_bill_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPLICABLE',

    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sic_amounts CHECK (
        original_grand_total >= 0
        AND previously_paid_amount >= 0
        AND previously_returned_amount >= 0
        AND previously_refunded_amount >= 0
        AND cancelled_invoice_amount >= 0
        AND refund_amount >= 0
        AND receivable_adjustment_amount >= 0
    ),
    CONSTRAINT chk_sic_refund_method CHECK (
        refund_method IS NULL OR refund_method IN ('CASH', 'UPI', 'CARD', 'BANK')
    ),
    CONSTRAINT chk_sic_refund_status CHECK (
        refund_status IN ('NOT_REQUIRED', 'PENDING', 'PROCESSING', 'SUCCESSFUL', 'FAILED', 'REVERSED')
    ),
    CONSTRAINT chk_sic_gst_adjustment_type CHECK (
        gst_adjustment_type IN ('INVOICE_CANCELLATION', 'CREDIT_NOTE_REQUIRED', 'NO_TAX_ADJUSTMENT', 'PERIOD_LOCKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_sic_sale_id ON sales_invoice_cancellations(sale_id);
CREATE INDEX IF NOT EXISTS idx_sic_cancelled_at ON sales_invoice_cancellations(cancelled_at DESC);
CREATE INDEX IF NOT EXISTS idx_sic_invoice_number ON sales_invoice_cancellations(invoice_number);

-- 5. Create sales_invoice_cancellation_items table (prevents duplicate stock restoration per line item)
CREATE TABLE IF NOT EXISTS sales_invoice_cancellation_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    cancellation_id BIGINT NOT NULL
        REFERENCES sales_invoice_cancellations(id) ON DELETE RESTRICT,
    sales_product_id BIGINT NOT NULL UNIQUE
        REFERENCES sales_products(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL
        REFERENCES products(id) ON DELETE RESTRICT,
    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    original_quantity NUMERIC(14,3) NOT NULL,
    previously_returned_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
    restored_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL,
    taxable_reversed NUMERIC(14,2) NOT NULL DEFAULT 0,
    cgst_reversed NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_reversed NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_reversed NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_tax_reversed NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total_reversed NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sici_quantities CHECK (
        original_quantity > 0
        AND previously_returned_quantity >= 0
        AND restored_quantity >= 0
        AND previously_returned_quantity + restored_quantity <= original_quantity
    )
);

CREATE INDEX IF NOT EXISTS idx_sici_cancellation_id ON sales_invoice_cancellation_items(cancellation_id);
CREATE INDEX IF NOT EXISTS idx_sici_product_id ON sales_invoice_cancellation_items(product_id);

-- 6. Create sales_cancellation_refunds table (traceable to original sales_payments and refund lifecycle)
CREATE TABLE IF NOT EXISTS sales_cancellation_refunds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    cancellation_id BIGINT NOT NULL
        REFERENCES sales_invoice_cancellations(id) ON DELETE RESTRICT,
    sale_id BIGINT NOT NULL
        REFERENCES sales(id) ON DELETE RESTRICT,
    original_payment_id BIGINT
        REFERENCES sales_payments(id) ON DELETE RESTRICT,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reference_number VARCHAR(100),
    refund_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    confirmed_by BIGINT
        REFERENCES users(id) ON DELETE RESTRICT,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_scr_payment_method CHECK (
        payment_method IN ('CASH', 'UPI', 'CARD', 'BANK')
    ),
    CONSTRAINT chk_scr_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCESSFUL', 'FAILED', 'REVERSED')
    )
);

CREATE INDEX IF NOT EXISTS idx_scr_cancellation_id ON sales_cancellation_refunds(cancellation_id);
CREATE INDEX IF NOT EXISTS idx_scr_sale_id ON sales_cancellation_refunds(sale_id);
CREATE INDEX IF NOT EXISTS idx_scr_original_payment_id ON sales_cancellation_refunds(original_payment_id);
