ALTER TABLE purchase_items RENAME TO purchase_products;
ALTER SEQUENCE purchase_items_id_seq RENAME TO purchase_products_id_seq;

ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_id_not_null
        TO purchase_products_id_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_uuid_not_null
        TO purchase_products_uuid_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_purchase_id_not_null
        TO purchase_products_purchase_id_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_product_id_not_null
        TO purchase_products_product_id_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_quantity_not_null
        TO purchase_products_quantity_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_purchase_price_not_null
        TO purchase_products_purchase_price_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_discount_amount_not_null
        TO purchase_products_discount_amount_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_tax_percent_not_null
        TO purchase_products_tax_percent_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_tax_amount_not_null
        TO purchase_products_tax_amount_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_line_total_not_null
        TO purchase_products_line_total_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_created_at_not_null
        TO purchase_products_created_at_not_null;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_pkey TO purchase_products_pkey;
ALTER TABLE purchase_products
    RENAME CONSTRAINT purchase_items_uuid_key TO purchase_products_uuid_key;
ALTER TABLE purchase_products
    RENAME CONSTRAINT fk_purchase_items_purchase
        TO fk_purchase_products_purchase;
ALTER TABLE purchase_products
    RENAME CONSTRAINT fk_purchase_items_product
        TO fk_purchase_products_product;
ALTER TABLE purchase_products
    RENAME CONSTRAINT uq_purchase_item_product
        TO uq_purchase_products_product;
ALTER TABLE purchase_products
    RENAME CONSTRAINT chk_purchase_item_quantity
        TO chk_purchase_products_quantity;
ALTER TABLE purchase_products
    RENAME CONSTRAINT chk_purchase_item_price
        TO chk_purchase_products_price;
ALTER TABLE purchase_products
    RENAME CONSTRAINT chk_purchase_item_discount
        TO chk_purchase_products_discount;
ALTER TABLE purchase_products
    RENAME CONSTRAINT chk_purchase_item_tax_percent
        TO chk_purchase_products_tax_percent;
ALTER TABLE purchase_products
    RENAME CONSTRAINT chk_purchase_item_tax_amount
        TO chk_purchase_products_tax_amount;
ALTER TABLE purchase_products
    RENAME CONSTRAINT chk_purchase_item_line_total
        TO chk_purchase_products_line_total;
ALTER INDEX idx_purchase_items_product_id
    RENAME TO idx_purchase_products_product_id;

ALTER TABLE sales_items RENAME TO sales_products;
ALTER SEQUENCE sales_items_id_seq RENAME TO sales_products_id_seq;

ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_id_not_null
        TO sales_products_id_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_uuid_not_null
        TO sales_products_uuid_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_sale_id_not_null
        TO sales_products_sale_id_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_product_id_not_null
        TO sales_products_product_id_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_product_code_not_null
        TO sales_products_product_code_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_product_name_not_null
        TO sales_products_product_name_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_quantity_not_null
        TO sales_products_quantity_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_unit_not_null
        TO sales_products_unit_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_unit_price_not_null
        TO sales_products_unit_price_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_discount_amount_not_null
        TO sales_products_discount_amount_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_taxable_amount_not_null
        TO sales_products_taxable_amount_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_gst_rate_not_null
        TO sales_products_gst_rate_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_cgst_rate_not_null
        TO sales_products_cgst_rate_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_sgst_rate_not_null
        TO sales_products_sgst_rate_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_igst_rate_not_null
        TO sales_products_igst_rate_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_cgst_amount_not_null
        TO sales_products_cgst_amount_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_sgst_amount_not_null
        TO sales_products_sgst_amount_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_igst_amount_not_null
        TO sales_products_igst_amount_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_total_tax_amount_not_null
        TO sales_products_total_tax_amount_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_line_total_not_null
        TO sales_products_line_total_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_created_at_not_null
        TO sales_products_created_at_not_null;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_pkey TO sales_products_pkey;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_uuid_key TO sales_products_uuid_key;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_sale_id_fkey
        TO sales_products_sale_id_fkey;
ALTER TABLE sales_products
    RENAME CONSTRAINT sales_items_product_id_fkey
        TO sales_products_product_id_fkey;
ALTER TABLE sales_products
    RENAME CONSTRAINT chk_sales_item_quantity
        TO chk_sales_product_quantity;
ALTER TABLE sales_products
    RENAME CONSTRAINT chk_sales_item_amounts
        TO chk_sales_product_amounts;
ALTER INDEX idx_sales_items_sale_id
    RENAME TO idx_sales_products_sale_id;
ALTER INDEX idx_sales_items_product_id
    RENAME TO idx_sales_products_product_id;

ALTER TABLE products
    RENAME CONSTRAINT products_item_id_not_null
        TO products_product_code_not_null;
