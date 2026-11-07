
-- V20__standardize_cash_session_difference_amount.sql

-- Copy existing closing differences into the new column.
-- Leave OPEN sessions as NULL.
UPDATE cash_register_sessions
SET difference_amount = difference
WHERE difference_amount IS NULL
  AND difference IS NOT NULL;

-- Remove the old constraint.
ALTER TABLE cash_register_sessions
DROP CONSTRAINT chk_cash_session_closing_values;

-- Remove the old column.
ALTER TABLE cash_register_sessions
DROP COLUMN difference;

-- Recreate the constraint using difference_amount.
ALTER TABLE cash_register_sessions
ADD CONSTRAINT chk_cash_session_closing_values
CHECK (
    (
        status = 'OPEN'
        AND closed_at IS NULL
        AND closed_by IS NULL
        AND actual_closing_balance IS NULL
        AND difference_amount IS NULL
    )
    OR
    (
        status = 'CLOSED'
        AND closed_at IS NOT NULL
        AND closed_by IS NOT NULL
        AND actual_closing_balance IS NOT NULL
        AND expected_closing_balance IS NOT NULL
        AND difference_amount IS NOT NULL
    )
);
