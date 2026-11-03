CREATE TABLE cash_registers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    register_code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cash_register_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    cash_register_id BIGINT NOT NULL,
    opened_by BIGINT NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    opening_balance NUMERIC(14, 2) NOT NULL,

    closed_by BIGINT,
    closed_at TIMESTAMPTZ,

    expected_closing_balance NUMERIC(14, 2),
    actual_closing_balance NUMERIC(14, 2),
    difference NUMERIC(14, 2),

    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    CONSTRAINT fk_cash_session_register
        FOREIGN KEY (cash_register_id)
        REFERENCES cash_registers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_cash_session_opened_by
        FOREIGN KEY (opened_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_cash_session_closed_by
        FOREIGN KEY (closed_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_cash_session_opening_balance
        CHECK (opening_balance >= 0),

    CONSTRAINT chk_cash_session_status
        CHECK (status IN ('OPEN', 'CLOSED')),

    CONSTRAINT chk_cash_session_closing_values
        CHECK (
            (status = 'OPEN'
                AND closed_at IS NULL
                AND closed_by IS NULL
                AND actual_closing_balance IS NULL)
            OR
            (status = 'CLOSED'
                AND closed_at IS NOT NULL
                AND closed_by IS NOT NULL
                AND actual_closing_balance IS NOT NULL
                AND expected_closing_balance IS NOT NULL
                AND difference IS NOT NULL)
        )
);

-- Only one open session per physical register.
CREATE UNIQUE INDEX uq_cash_register_open_session
    ON cash_register_sessions(cash_register_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_cash_sessions_register
    ON cash_register_sessions(cash_register_id);

CREATE INDEX idx_cash_sessions_opened_at
    ON cash_register_sessions(opened_at);


CREATE TABLE cash_register_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    session_id BIGINT NOT NULL,

    movement_type VARCHAR(10) NOT NULL,
    source_type VARCHAR(30) NOT NULL,

    -- UUID of the original sale payment, purchase payment,
    -- expense, refund, or other source transaction.
    source_uuid UUID,

    amount NUMERIC(14, 2) NOT NULL,
    description VARCHAR(500),

    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cash_movement_session
        FOREIGN KEY (session_id)
        REFERENCES cash_register_sessions(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_cash_movement_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_cash_movement_type
        CHECK (movement_type IN ('IN', 'OUT')),

    CONSTRAINT chk_cash_movement_source
        CHECK (
            source_type IN (
                'MANUAL',
                'SALE_PAYMENT',
                'PURCHASE_PAYMENT',
                'EXPENSE',
                'SALES_REFUND',
                'CUSTOMER_COLLECTION',
                'ADJUSTMENT'
            )
        ),

    CONSTRAINT chk_cash_movement_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_cash_movements_session
    ON cash_register_movements(session_id);

CREATE INDEX idx_cash_movements_created_at
    ON cash_register_movements(created_at);

CREATE INDEX idx_cash_movements_source
    ON cash_register_movements(source_type, source_uuid);

-- Prevent the same source transaction from being posted twice.
CREATE UNIQUE INDEX uq_cash_movement_source
    ON cash_register_movements(source_type, source_uuid)
    WHERE source_uuid IS NOT NULL; 