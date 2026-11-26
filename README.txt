Dynamic Sales Status Backend Patch

Copy the included src/ files over the matching paths in the backend project.
Flyway migration V44 creates sales_statuses and sales_payment_statuses, seeds the
requested status/payment values, replaces the old CHECK constraints with foreign
keys, and preserves existing sales rows.

New read endpoints:
- GET /api/v1/sales/status-options
- GET /api/v1/sales/payment-status-options

Both endpoints require SALES.VIEW.

Draft balance is now grand_total - paid_amount and its payment status is calculated.
Backend creation/completion/cancellation/payment logic checks that the applicable
status code exists and is active in the corresponding master table.

The lifecycle transition rules remain enforced by the sales service; the lookup
tables drive the available status values and labels exposed to the UI.
