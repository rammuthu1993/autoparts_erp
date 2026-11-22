-- =========================================================
-- V35: Performance Indexes for Catalog & Master Queries
-- Enables high-speed case-insensitive searches and pagination
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_products_part_no_lower
    ON products (LOWER(part_no));

CREATE INDEX IF NOT EXISTS idx_products_product_code_lower
    ON products (LOWER(product_code));

CREATE INDEX IF NOT EXISTS idx_customers_code_lower
    ON customers (LOWER(customer_code));
