-- V52: Enhance tax_rates and product_tax_mapping for safe effective-period closure,
-- superseding, HSN descriptions, audit metadata, and PBAC permissions.

ALTER TABLE tax_rates
    ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500);

ALTER TABLE product_tax_mapping
    ADD COLUMN IF NOT EXISTS hsn_description VARCHAR(255),
    ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_product_tax_mapping_hsn_code
    ON product_tax_mapping(hsn_code)
    WHERE hsn_code IS NOT NULL;

-- PostgreSQL exclusion constraint to enforce non-overlapping [effective_from, effective_to)
-- intervals per product at the database level (requires btree_gist extension).
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS btree_gist;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'excl_product_tax_mapping_effective_range'
    ) THEN
        ALTER TABLE product_tax_mapping
            ADD CONSTRAINT excl_product_tax_mapping_effective_range
            EXCLUDE USING gist (
                product_id WITH =,
                daterange(effective_from, COALESCE(effective_to, '9999-12-31'::date), '[)') WITH &&
            );
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- If superuser privileges are restricted for CREATE EXTENSION in a managed DB,
        -- fall back to application-level locking + unique constraints without failing migration.
        RAISE NOTICE 'Skipping btree_gist exclusion constraint: %', SQLERRM;
END $$;

INSERT INTO permissions (code, name, description, module, action) VALUES
    ('PRODUCT_TAX_MAPPINGS.MANAGE', 'Manage Product Tax Mappings', 'Assign, close, supersede, and bulk-assign product GST mappings', 'TAX_RATES', 'MAPPING_MANAGE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('ADMIN', 'MANAGER')
  AND p.code = 'PRODUCT_TAX_MAPPINGS.MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;
