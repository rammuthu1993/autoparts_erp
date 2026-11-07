
-- =========================================================
-- DAILY CLOSINGS
-- One consolidated closing per business date
-- =========================================================

CREATE TABLE daily_closings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    business_date DATE NOT NULL UNIQUE,

    closed_by BIGINT NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,

    closed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    notes VARCHAR(1000),

    -- SALES
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    sales_paid_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_balance_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- SALES PAYMENT COLLECTIONS
    sales_cash_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_upi_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_card_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_bank_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_credit_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- SALES RETURNS
    sales_return_count BIGINT NOT NULL DEFAULT 0,
    sales_return_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- SALES REFUNDS
    sales_refund_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_refund_cash_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_refund_upi_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_refund_card_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_refund_bank_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_refund_credit_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- PURCHASES
    purchase_count BIGINT NOT NULL DEFAULT 0,
    purchase_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- PURCHASE PAYMENTS
    purchase_cash_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    purchase_upi_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    purchase_card_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    purchase_bank_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- EXPENSES
    expense_count BIGINT NOT NULL DEFAULT 0,
    expense_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    expense_cash_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    expense_upi_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    expense_card_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    expense_bank_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- CASH REGISTER TOTALS
    register_count BIGINT NOT NULL DEFAULT 0,

    cash_opening_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_in_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_out_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    cash_expected_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_actual_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_difference_total NUMERIC(14,2) NOT NULL DEFAULT 0,

    CONSTRAINT chk_daily_closing_counts CHECK (
        sales_count >= 0
        AND sales_return_count >= 0
        AND purchase_count >= 0
        AND expense_count >= 0
        AND register_count >= 0
    )
);

CREATE INDEX idx_daily_closings_business_date
    ON daily_closings(business_date DESC);

CREATE INDEX idx_daily_closings_closed_at
    ON daily_closings(closed_at);


-- =========================================================
-- DAILY CLOSING CASH REGISTER DETAILS
-- One row per closed cash session included in the closing
-- =========================================================

CREATE TABLE daily_closing_cash_registers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    daily_closing_id BIGINT NOT NULL
        REFERENCES daily_closings(id) ON DELETE RESTRICT,

    cash_register_session_id BIGINT NOT NULL
        REFERENCES cash_register_sessions(id) ON DELETE RESTRICT,

    register_name VARCHAR(100) NOT NULL,

    opening_balance NUMERIC(14,2) NOT NULL DEFAULT 0,

    total_cash_in NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_cash_out NUMERIC(14,2) NOT NULL DEFAULT 0,

    expected_closing_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    actual_closing_balance NUMERIC(14,2) NOT NULL DEFAULT 0,

    difference_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    CONSTRAINT uq_daily_closing_session
        UNIQUE(daily_closing_id, cash_register_session_id),

    CONSTRAINT uq_daily_closing_session_global
        UNIQUE(cash_register_session_id)
);

CREATE INDEX idx_daily_closing_registers_closing_id
    ON daily_closing_cash_registers(daily_closing_id);
