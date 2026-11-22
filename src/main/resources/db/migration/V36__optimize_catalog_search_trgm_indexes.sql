-- ====================================================================
-- V36: Accelerated Substring Search Indexes via pg_trgm (GIN)
-- Accelerates leading-wildcard LIKE '%term%' searches on catalog masters
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Products: Accelerated substring search on name, part number, and code
CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING gin (LOWER(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_products_part_no_trgm
    ON products USING gin (LOWER(part_no) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_products_product_code_trgm
    ON products USING gin (LOWER(product_code) gin_trgm_ops);

-- Products: B-tree index on barcode for exact lookup
CREATE INDEX IF NOT EXISTS idx_products_barcode
    ON products (barcode)
    WHERE barcode IS NOT NULL;

-- Customers: Accelerated substring search on customer name and customer code
CREATE INDEX IF NOT EXISTS idx_customers_name_trgm
    ON customers USING gin (LOWER(customer_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_customers_code_trgm
    ON customers USING gin (LOWER(customer_code) gin_trgm_ops);

-- Suppliers: Accelerated substring search on supplier name and contact person
CREATE INDEX IF NOT EXISTS idx_suppliers_name_trgm
    ON suppliers USING gin (LOWER(supplier_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_suppliers_contact_trgm
    ON suppliers USING gin (LOWER(contact_person) gin_trgm_ops);
