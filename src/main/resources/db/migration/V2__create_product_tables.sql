CREATE TABLE product_categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_categories_name_lower
    ON product_categories (LOWER(name));


CREATE TABLE product_brands (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_brands_name_lower
    ON product_brands (LOWER(name));


CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    item_id VARCHAR(50) NOT NULL UNIQUE,
    part_no VARCHAR(100) NOT NULL UNIQUE,

    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),

    category_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,

    purchase_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    qty NUMERIC(12, 3) NOT NULL DEFAULT 0,
    minimum_stock NUMERIC(12, 3) NOT NULL DEFAULT 0,

    unit VARCHAR(20) NOT NULL DEFAULT 'PCS',
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_items_category
        FOREIGN KEY (category_id) REFERENCES product_categories(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_items_brand
        FOREIGN KEY (brand_id) REFERENCES product_brands(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_items_purchase_price
        CHECK (purchase_price >= 0),

    CONSTRAINT chk_items_selling_price
        CHECK (selling_price >= 0),

    CONSTRAINT chk_items_qty
        CHECK (qty >= 0),

    CONSTRAINT chk_items_minimum_stock
        CHECK (minimum_stock >= 0),

    CONSTRAINT chk_items_unit
        CHECK (LENGTH(TRIM(unit)) > 0)
);

CREATE INDEX idx_items_name ON products (LOWER(name));
CREATE INDEX idx_items_category_id ON products (category_id);
CREATE INDEX idx_items_brand_id ON products (brand_id);
CREATE INDEX idx_items_active ON products (active);

CREATE INDEX idx_items_low_stock
    ON products (qty, minimum_stock)
    WHERE active = TRUE;