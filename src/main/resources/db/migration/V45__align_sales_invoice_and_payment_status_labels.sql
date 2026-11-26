-- Align sales status masters with the invoice/payment lifecycle.
-- COMPLETED remains the persisted enum code for compatibility; its user-facing label is Issued.
UPDATE sales_statuses
SET label = 'Issued', active = TRUE, sort_order = 20, updated_at = CURRENT_TIMESTAMP
WHERE code = 'COMPLETED';

-- PENDING was previously used as an invoice status. Keep the code for historical
-- compatibility, but remove it from active invoice-status choices.
UPDATE sales_statuses
SET active = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE code = 'PENDING';

UPDATE sales_statuses
SET label = 'Draft', active = TRUE, sort_order = 10, updated_at = CURRENT_TIMESTAMP
WHERE code = 'DRAFT';

UPDATE sales_statuses
SET label = 'Cancelled', active = TRUE, sort_order = 30, updated_at = CURRENT_TIMESTAMP
WHERE code = 'CANCELLED';

UPDATE sales_payment_statuses
SET label = 'Unpaid', active = TRUE, sort_order = 10, updated_at = CURRENT_TIMESTAMP
WHERE code = 'UNPAID';

UPDATE sales_payment_statuses
SET label = 'Partially Paid', active = TRUE, sort_order = 20, updated_at = CURRENT_TIMESTAMP
WHERE code = 'PARTIALLY_PAID';

UPDATE sales_payment_statuses
SET label = 'Paid', active = TRUE, sort_order = 30, updated_at = CURRENT_TIMESTAMP
WHERE code = 'PAID';
