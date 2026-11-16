# ERP AI Purchase Lookup Patch

This patch adds the first read-only Purchase capability to the existing custom LangGraph4j workflow.

## What it adds

- `PURCHASE_SEARCH`: list recent purchases, optionally filter by supplier name, purchase number, or supplier invoice number.
- `PURCHASE_DETAILS`: retrieve a purchase using its exact `PUR-...` purchase number or supplier invoice number, including product lines and totals.
- Deterministic Java response formatting for purchase search and detail results.
- Result limits clamped to 1–50 for purchase searches and a bounded detail result set.
- Purchase lookups restricted to `ADMIN` and `MANAGER` through the existing `@AdminOrManager` method-security annotation.
- Access-denied exceptions are propagated instead of being converted into a generic tool error.
- Unit tests for intent classification, routing, tool execution, SQL parameters, and response formatting.

## Database compatibility

No Flyway migration is included or required. The SQL uses the schema already present in the project:

- `purchases`
- `suppliers`
- `purchase_products` (renamed from `purchase_items` by V11)
- `products`

The `purchase_products.product_id` relationship remains a numeric foreign key to `products.id`.

## Apply

Extract this ZIP into the project root (`autoparts-erp`) and merge/overwrite the included `src` files, preserving the directory structure. It does not include or modify secrets, Flyway history, V29/V30, or `FlywayRepairConfig.java`.

## Verify on Windows

Run the focused tests first:

```cmd
mvnw.cmd -Dtest=ErpAiIntentClassifierTest,ErpAiGraphNodesTest,ErpAiPurchaseLookupTest,PurchaseLookupToolTest test
```

Then run the full suite:

```cmd
mvnw.cmd clean test
```

## Example prompts

```text
Show recent purchases
Show the last 5 purchases
Show purchases from supplier ABC Auto Parts
Show purchase details for PUR-00000012
Show purchase invoice number INV/2026/009
```

Purchase creation, receiving, cancellation, and payment are intentionally not exposed by this patch. Those operations need a separate preview-and-confirm workflow before any write is executed.
