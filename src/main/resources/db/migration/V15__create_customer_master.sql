
CREATE SEQUENCE customer_code_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    customer_code VARCHAR(50) NOT NULL UNIQUE,

    customer_name VARCHAR(200) NOT NULL,

    phone VARCHAR(20),
    email VARCHAR(150),

    address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(100),
    pincode VARCHAR(10),

    gstin VARCHAR(15),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_name
    ON customers (LOWER(customer_name));

CREATE INDEX idx_customers_phone
    ON customers (phone);

CREATE INDEX idx_customers_active
    ON customers (active);

ALTER TABLE sales
    ADD COLUMN customer_id BIGINT;

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_customer
    FOREIGN KEY (customer_id)
    REFERENCES customers(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_sales_customer_id
    ON sales(customer_id);
