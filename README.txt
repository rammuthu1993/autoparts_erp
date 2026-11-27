Industry-standard sales and purchase returns improvements

Copy the included src/ files into the backend project.

Changes:
- SalesReturnResponse now includes a human-readable returnNumber (SR-yyyyMMdd-000001)
  while retaining UUID for API identity.
- Added GET /api/v1/purchases/{purchaseUuid}/returns to retrieve the purchase's
  return documents with line-level details and supplier credit information.
- Added a non-locking purchase lookup for the read-only history endpoint.

No database migration is required.

Frontend changes:
- Sales return form shows sold, previously returned, remaining, and return-now quantities.
- Purchase return form shows received, previously returned, remaining, and return-now quantities.
- Inputs are disabled while history loads or when remaining quantity is zero.
- Previous sales and purchase returns are listed and open with line-item details.
- Sales invoice list shows No Returns / Partially Returned / Fully Returned without
  hiding or changing the original invoice.
- Product master has a View Details modal using GET /api/v1/products/{uuid}.
- Action buttons in the touched screens use icons and black tooltips.

Important:
- Return quantities remain validated by the backend; the frontend is not the authority.
- Existing accepted returns are aggregated by original invoice-line UUID.
