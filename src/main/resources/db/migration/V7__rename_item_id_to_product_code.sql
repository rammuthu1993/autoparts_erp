ALTER TABLE products
    RENAME COLUMN item_id TO product_code;

ALTER TABLE products
    RENAME CONSTRAINT products_item_id_key TO uq_products_product_code;

ALTER TABLE products
    RENAME CONSTRAINT fk_items_category TO fk_products_category;

ALTER TABLE products
    RENAME CONSTRAINT fk_items_brand TO fk_products_brand;

ALTER TABLE products
    RENAME CONSTRAINT chk_items_purchase_price TO chk_products_purchase_price;

ALTER TABLE products
    RENAME CONSTRAINT chk_items_selling_price TO chk_products_selling_price;

ALTER TABLE products
    RENAME CONSTRAINT chk_items_qty TO chk_products_qty;

ALTER TABLE products
    RENAME CONSTRAINT chk_items_minimum_stock TO chk_products_minimum_stock;

ALTER TABLE products
    RENAME CONSTRAINT chk_items_unit TO chk_products_unit;

ALTER INDEX idx_items_name RENAME TO idx_products_name;
ALTER INDEX idx_items_category_id RENAME TO idx_products_category_id;
ALTER INDEX idx_items_brand_id RENAME TO idx_products_brand_id;
ALTER INDEX idx_items_active RENAME TO idx_products_active;
ALTER INDEX idx_items_low_stock RENAME TO idx_products_low_stock;

ALTER SEQUENCE product_item_id_seq RENAME TO product_code_seq;
