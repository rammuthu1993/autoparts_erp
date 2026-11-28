-- Enhance product_categories and product_brands with master-data codes, hierarchy/metadata, and filtering indexes

ALTER TABLE product_categories
    ADD COLUMN IF NOT EXISTS code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES product_categories(id) ON DELETE RESTRICT;

ALTER TABLE product_categories
    DROP CONSTRAINT IF EXISTS chk_product_categories_no_self_parent;

ALTER TABLE product_categories
    ADD CONSTRAINT chk_product_categories_no_self_parent
        CHECK (parent_id IS NULL OR parent_id <> id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_code_lower
    ON product_categories (LOWER(code))
    WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_categories_active
    ON product_categories (active);

CREATE INDEX IF NOT EXISTS idx_categories_parent_id
    ON product_categories (parent_id);

ALTER TABLE product_brands
    ADD COLUMN IF NOT EXISTS code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS country_of_origin VARCHAR(100),
    ADD COLUMN IF NOT EXISTS website VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_brands_code_lower
    ON product_brands (LOWER(code))
    WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_brands_active
    ON product_brands (active);
