# API and database compatibility

The product business identifier is named `productCode` in JSON and
`product_code` in the database. Existing product-code values are preserved.

- Product responses replace the legacy `itemId` property with `productCode`.
  Generated values keep their existing `GAP-######` format.
- The duplicate-code error changes from the legacy
  `PRODUCT_ITEM_ID_ALREADY_EXISTS` identifier to `PRODUCT_CODE_ALREADY_EXISTS`.
- Purchase product responses expose `productCode`; `productId` remains the
  numeric `products.id` foreign key.
- Purchase creation requests continue to submit numeric `productId` values.
- Sales and purchase request/response line collections use `products`.
  Endpoint paths and JSON properties are unchanged by the transaction-table
  rename.

Flyway migration V11 renames the purchase and sales line tables and their
related constraints, indexes, and identity sequences. It preserves rows and
the numeric `product_id` foreign keys to `products.id`. Applications that
query the database tables directly must update their SQL to use
`purchase_products` and `sales_products`.

Clients reading the legacy product response field must migrate to
`productCode` when deploying the product-code API change.
