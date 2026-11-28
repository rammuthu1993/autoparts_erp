-- =============================================================================
-- Migration: V47__complete_erp_workflows.sql
-- Description: Complete production workflow enhancements across Purchase,
--              Inventory, Cash Register, Daily Closing, Sales Returns, and
--              Purchase Returns.
-- =============================================================================

-- 1. Additional permissions for complete module workflows
INSERT INTO permissions (code, name, description, module, action) VALUES
    ('PURCHASES.UPDATE', 'Update Draft Purchases', 'Edit draft purchase orders before receiving', 'PURCHASES', 'UPDATE'),
    ('PURCHASES.CANCEL', 'Cancel Draft Purchases', 'Cancel draft purchase orders before receiving', 'PURCHASES', 'CANCEL'),
    ('INVENTORY.RECONCILE', 'Reconcile Inventory', 'Perform physical stock count reconciliation', 'INVENTORY', 'RECONCILE'),
    ('SALES_RETURNS.INSPECT', 'Inspect Sales Returns', 'Inspect returned items and assign restock/quarantine/scrap disposition', 'SALES_RETURNS', 'INSPECT')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'PURCHASES.UPDATE',
    'PURCHASES.CANCEL',
    'INVENTORY.RECONCILE',
    'SALES_RETURNS.INSPECT'
)
WHERE r.name IN ('ADMIN', 'MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2. Inventory: quarantine & scrap tracking on products + reconciliation movement type
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS quarantine_qty NUMERIC(12,3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS scrap_qty NUMERIC(12,3) NOT NULL DEFAULT 0;

ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_quarantine_qty;
ALTER TABLE products
    ADD CONSTRAINT chk_products_quarantine_qty CHECK (quarantine_qty >= 0);

ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_scrap_qty;
ALTER TABLE products
    ADD CONSTRAINT chk_products_scrap_qty CHECK (scrap_qty >= 0);

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
            'RECONCILIATION',
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
            'RECONCILIATION',
            'OPENING_STOCK'
        ));

-- 3. Cash Register Sessions: opening notes, Indian denominations, discrepancy reason
ALTER TABLE cash_register_sessions
    ADD COLUMN IF NOT EXISTS opening_notes VARCHAR(500),
    ADD COLUMN IF NOT EXISTS denomination_500 INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS denomination_200 INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS denomination_100 INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS denomination_50 INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS denomination_20 INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS denomination_10 INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS coins_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS denominations_used BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS difference_reason VARCHAR(500);

ALTER TABLE cash_register_sessions DROP CONSTRAINT IF EXISTS chk_cash_session_denominations;
ALTER TABLE cash_register_sessions
    ADD CONSTRAINT chk_cash_session_denominations CHECK (
        denomination_500 >= 0
        AND denomination_200 >= 0
        AND denomination_100 >= 0
        AND denomination_50 >= 0
        AND denomination_20 >= 0
        AND denomination_10 >= 0
        AND coins_amount >= 0
    );

-- 4. Sales Return Products: inspection & disposition (RESTOCK, QUARANTINE, SCRAP)
ALTER TABLE sales_return_products
    ADD COLUMN IF NOT EXISTS disposition VARCHAR(20) NOT NULL DEFAULT 'RESTOCK',
    ADD COLUMN IF NOT EXISTS condition_notes VARCHAR(300),
    ADD COLUMN IF NOT EXISTS inspection_notes VARCHAR(500);

ALTER TABLE sales_return_products DROP CONSTRAINT IF EXISTS chk_sales_return_product_disposition;
ALTER TABLE sales_return_products
    ADD CONSTRAINT chk_sales_return_product_disposition
        CHECK (disposition IN ('RESTOCK', 'QUARANTINE', 'SCRAP'));

-- 5. Purchase Return Products: condition & disposition
ALTER TABLE purchase_return_products
    ADD COLUMN IF NOT EXISTS disposition VARCHAR(30) NOT NULL DEFAULT 'RETURN_TO_SUPPLIER',
    ADD COLUMN IF NOT EXISTS condition_notes VARCHAR(300);

-- 6. Idempotency keys for financial transactions
ALTER TABLE purchase_payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) UNIQUE;

ALTER TABLE sales_refunds
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) UNIQUE;

ALTER TABLE supplier_refunds
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) UNIQUE;

ALTER TABLE supplier_credit_applications
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) UNIQUE;

-- 7. Supporting indexes for paginated search & date filters
CREATE INDEX IF NOT EXISTS idx_purchases_created_at ON purchases(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sales_returns_status ON sales_returns(status);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_return_number ON purchase_returns(return_number);
