# ERP workflow implementation patch

This patch is deliberately additive and preserves existing API contracts.

## Included in this patch

### Product master
- `GET /api/v1/units/active`: active unit-of-measure lookup. Existing `products.unit`
  remains a string for compatibility; the UI can now populate it from this master.
- `GET /api/v1/products/search?q=...&page=0&size=20`: POS-friendly alias over the
  existing paged product search (which already searches product identity fields).
- `GET /api/v1/products/{uuid}/price-history`: historical purchase/selling prices.
- Flyway V47 creates the unit master, seeds common units, creates product price
  history and installs a database trigger. Product creation and price changes are
  captured in the same database transaction as the product write.

### Returns and cash reconciliation schema foundation
- Flyway V47 adds return inspection metadata and a per-return-line stock disposition
  (`RESTOCK` by default to preserve current behaviour).
- Flyway V47 adds closing notes, discrepancy reason and optional denomination JSON
  to cash sessions. Existing close-session request/response contracts remain valid.
- A persisted idempotency-key registry is added for subsequent payment/refund API
  integration. This patch does not claim the current payment endpoints are idempotent.

## Existing APIs retained
- Sales create/complete/payment/cancel.
- Sales returns create/list/detail/refund.
- Cash register open/close/latest/movements.
- Product create/update/deactivate/barcode and paged register.

## Important follow-up work not implemented in this patch

The audit identified additional workflow changes that require coordinated service,
ledger and UI work and must not be approximated with schema-only changes:

1. Draft invoice update and a server-side completion/payment preview.
2. Payment/refund pending-success-failed-reversed lifecycle and idempotency enforcement.
3. Credit-note issue/void lifecycle and customer receivable posting. GST/IRN rules need
   accountant validation before enabling this in production.
4. Return inspection/accept/reject service actions and stock disposition posting.
   The current automatic accepted-return flow remains unchanged.
5. Dedicated paged sales-return register and summary queries.
6. Cash session detail/current/history/summary APIs, denomination capture in the
   close-session command, discrepancy validation/approval, and register-scoped
   transaction posting. Existing movement and session behaviour remains unchanged.
7. Unit validation on product create/update (legacy unit text is still accepted).
8. Product alternatives, vehicle compatibility, and price-change actor attribution.
9. End-to-end tests for invoice, stock, return, refund and cash reconciliation.

These items are intentionally called out rather than silently introducing endpoint
contracts that are not backed by transactional service logic. The migration provides
the additive database foundation for several of them.

## Migration notes

- Apply Flyway migration `V47__erp_workflow_foundation.sql` using the normal
  application startup/migration process.
- Existing unit strings are not rewritten.
- Existing returns are marked `NOT_REQUIRED` for inspection and their lines default
  to `RESTOCK`, matching current behaviour.
- Existing sessions receive null reconciliation notes/denomination data.
- The new `api_idempotency_keys` table is not active until an operation explicitly
  uses it.

## Validation

The Java source changes are small Spring JDBC controllers plus one route addition.
A Maven compile could not be completed in this environment because the Maven Wrapper
could not download Maven 3.10.0 (`repo.maven.apache.org` fetch failed). Run
`./mvnw clean test` in the normal development environment and apply V47 to a
non-production database before deployment.
