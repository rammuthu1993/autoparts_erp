CREATE TABLE inventory_adjustments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    reason VARCHAR(250) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_inventory_adjustment_reason
        CHECK (LENGTH(TRIM(reason)) > 0)
);

CREATE INDEX idx_inventory_adjustments_created_at
    ON inventory_adjustments (created_at DESC);