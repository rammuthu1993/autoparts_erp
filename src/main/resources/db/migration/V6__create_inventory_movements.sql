
CREATE TABLE inventory_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    product_id BIGINT NOT NULL,
    movement_type VARCHAR(30) NOT NULL,

    reference_type VARCHAR(30) NOT NULL,
    reference_id BIGINT NOT NULL,

    quantity_change NUMERIC(12, 3) NOT NULL,
    quantity_before NUMERIC(12, 3) NOT NULL,
    quantity_after NUMERIC(12, 3) NOT NULL,

    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_movement_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_inventory_movement_type
        CHECK (movement_type IN (
            'PURCHASE_RECEIPT',
            'SALE',
            'SALES_RETURN',
            'PURCHASE_RETURN',
            'ADJUSTMENT',
            'OPENING_STOCK'
        )),

    CONSTRAINT chk_inventory_reference_type
        CHECK (reference_type IN (
            'PURCHASE',
            'SALE',
            'SALES_RETURN',
            'PURCHASE_RETURN',
            'ADJUSTMENT',
            'OPENING_STOCK'
        )),

    CONSTRAINT chk_inventory_quantity_change
        CHECK (quantity_change <> 0),

    CONSTRAINT chk_inventory_quantity_before
        CHECK (quantity_before >= 0),

    CONSTRAINT chk_inventory_quantity_after
        CHECK (quantity_after >= 0),

    CONSTRAINT chk_inventory_quantity_balance
        CHECK (quantity_after = quantity_before + quantity_change)
);

CREATE INDEX idx_inventory_movements_product
    ON inventory_movements(product_id, created_at DESC);

CREATE INDEX idx_inventory_movements_reference
    ON inventory_movements(reference_type, reference_id);
