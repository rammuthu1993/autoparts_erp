CREATE TABLE report_issue_resolutions (
    issue_key VARCHAR(200) PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes VARCHAR(1000),
    updated_by BIGINT NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_report_issue_resolution_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED'))
);

CREATE INDEX idx_report_issue_resolutions_status
    ON report_issue_resolutions(status, updated_at DESC);

CREATE TABLE report_targets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    metric_key VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    product_id BIGINT REFERENCES products(id) ON DELETE RESTRICT,
    category_id BIGINT REFERENCES product_categories(id) ON DELETE RESTRICT,
    target_value NUMERIC(14,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_report_target_metric
        CHECK (metric_key IN (
            'MONTHLY_SALES',
            'PRODUCT_SALES',
            'CATEGORY_REVENUE',
            'EXPENSE_BUDGET'
        )),
    CONSTRAINT chk_report_target_period
        CHECK (period_start <= period_end),
    CONSTRAINT chk_report_target_value
        CHECK (target_value >= 0),
    CONSTRAINT chk_report_target_dimension
        CHECK (
            (metric_key IN ('MONTHLY_SALES', 'EXPENSE_BUDGET')
                AND product_id IS NULL AND category_id IS NULL)
            OR (metric_key = 'PRODUCT_SALES'
                AND product_id IS NOT NULL AND category_id IS NULL)
            OR (metric_key = 'CATEGORY_REVENUE'
                AND category_id IS NOT NULL AND product_id IS NULL)
        )
);

CREATE UNIQUE INDEX uq_report_target_scope
    ON report_targets (
        metric_key,
        period_start,
        period_end,
        COALESCE(product_id, 0),
        COALESCE(category_id, 0)
    )
    WHERE active = TRUE;

CREATE INDEX idx_report_targets_period
    ON report_targets(period_start, period_end)
    WHERE active = TRUE;
