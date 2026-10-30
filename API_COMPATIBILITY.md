# API compatibility

The product business identifier is named `productCode` in JSON and
`product_code` in the database. Existing product-code values are preserved.

- Product responses replace `itemId` with `productCode`. Generated values keep
  their existing `GAP-######` format.
- The duplicate-code error changes from `PRODUCT_ITEM_ID_ALREADY_EXISTS` to
  `PRODUCT_CODE_ALREADY_EXISTS`.
- Purchase product responses expose `productCode`; `productId` remains the
  numeric `products.id` foreign key.
- Purchase creation requests continue to submit numeric `productId` values.
  Endpoint paths and purchase line collection names are unchanged.

Clients reading the old product response field must migrate from `itemId` to
`productCode` when deploying the database migration and updated application.
