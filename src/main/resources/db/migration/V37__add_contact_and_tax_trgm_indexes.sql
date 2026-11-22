-- ====================================================================
-- V37: Trigram Indexes for Secondary Customer and Supplier Search Predicates
-- Enables BitmapOr index scans across disjunctive catalog searches
-- covering phone and GSTIN identifiers.
-- ====================================================================

-- Customers: Substring search on phone numbers
CREATE INDEX IF NOT EXISTS idx_customers_phone_trgm
    ON customers USING gin (phone gin_trgm_ops);

-- Suppliers: Substring search on phone numbers and GSTIN identifiers
CREATE INDEX IF NOT EXISTS idx_suppliers_phone_trgm
    ON suppliers USING gin (phone gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_suppliers_gstin_trgm
    ON suppliers USING gin (gstin gin_trgm_ops);
