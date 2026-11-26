ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS round_off_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);

-- Existing issued invoice numbers are retained for audit and GST history.
-- Only newly completed invoices receive the financial-year sequence.
-- Drafts are assigned temporary IDs by the application.
