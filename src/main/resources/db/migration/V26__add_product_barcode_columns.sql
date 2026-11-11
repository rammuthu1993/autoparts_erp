ALTER TABLE products
    ADD COLUMN barcode VARCHAR(100),
    ADD COLUMN barcode_type VARCHAR(20) NOT NULL DEFAULT 'CODE_128';

-- Backfill existing products using their unique business product code.
UPDATE products
SET barcode = product_code
WHERE barcode IS NULL;

CREATE UNIQUE INDEX uq_products_barcode
    ON products (barcode)
    WHERE barcode IS NOT NULL;
