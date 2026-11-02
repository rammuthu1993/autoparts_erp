CREATE TABLE purchase_payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    purchase_id BIGINT NOT NULL,

    payment_method VARCHAR(20) NOT NULL,

    amount NUMERIC(14, 2) NOT NULL,

    reference_number VARCHAR(100),

    payment_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    notes VARCHAR(500),

    created_by BIGINT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_payments_purchase
        FOREIGN KEY (purchase_id)
        REFERENCES purchases(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_purchase_payments_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_purchase_payment_method
        CHECK (
            payment_method IN ('CASH', 'UPI', 'CARD', 'BANK')
        ),

    CONSTRAINT chk_purchase_payment_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_purchase_payments_purchase_id
    ON purchase_payments(purchase_id);

CREATE INDEX idx_purchase_payments_payment_date
    ON purchase_payments(payment_date);

CREATE INDEX idx_purchase_payments_created_by
    ON purchase_payments(created_by);