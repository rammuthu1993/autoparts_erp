
-- V8: GST configuration and product tax mapping

CREATE TABLE tax_rates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    tax_name VARCHAR(50) NOT NULL,

    gst_rate NUMERIC(5,2) NOT NULL,
    cgst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    sgst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    igst_rate NUMERIC(5,2) NOT NULL DEFAULT 0,

    effective_from DATE NOT NULL,
    effective_to DATE,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tax_rates_non_negative
        CHECK (
            gst_rate >= 0
            AND cgst_rate >= 0
            AND sgst_rate >= 0
            AND igst_rate >= 0
        ),

    CONSTRAINT chk_tax_rates_consistency
        CHECK (
            gst_rate = cgst_rate + sgst_rate
            AND gst_rate = igst_rate
        ),

    CONSTRAINT chk_tax_rates_effective_dates
        CHECK (
            effective_to IS NULL
            OR effective_to > effective_from
        ),

    CONSTRAINT uq_tax_rates_name_effective_from
        UNIQUE (tax_name, effective_from)
);


CREATE TABLE product_tax_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,

    product_id BIGINT NOT NULL,
    tax_rate_id BIGINT NOT NULL,

    hsn_code VARCHAR(20) NOT NULL,

    effective_from DATE NOT NULL,
    effective_to DATE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_product_tax_mapping_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_product_tax_mapping_tax_rate
        FOREIGN KEY (tax_rate_id)
        REFERENCES tax_rates(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_product_tax_mapping_effective_dates
        CHECK (
            effective_to IS NULL
            OR effective_to > effective_from
        ),

    CONSTRAINT uq_product_tax_mapping_effective_from
        UNIQUE (product_id, effective_from)
);


-- Only one mapping without an end date per product.
CREATE UNIQUE INDEX uq_product_tax_mapping_current
    ON product_tax_mapping(product_id)
    WHERE effective_to IS NULL;


CREATE INDEX idx_product_tax_mapping_tax_rate
    ON product_tax_mapping(tax_rate_id);


CREATE INDEX idx_product_tax_mapping_effective_dates
    ON product_tax_mapping(product_id, effective_from, effective_to);


CREATE INDEX idx_tax_rates_effective_dates
    ON tax_rates(effective_from, effective_to);
