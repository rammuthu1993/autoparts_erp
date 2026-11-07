# Reports & Analytics API (backend)

All endpoints are under `/api/v1/reports` and require authentication. Target
configuration and data-quality issue status changes require the `ADMIN` role.
Dates use ISO `yyyy-MM-dd` and are inclusive unless noted. Timestamped transaction
filters use the configured `app.business.time-zone` (default `Asia/Kolkata`);
generation timestamps are UTC.

## Endpoints

| Method and path | Purpose |
|---|---|
| `GET /dashboard?fromDate=&toDate=` | Existing dashboard summary |
| `GET /data-quality?fromDate=&toDate=` | Quality score and affected records |
| `PATCH /data-quality/issues/{issueKey}` | Admin-only resolution status and note |
| `GET /reconciliation?fromDate=&toDate=` | Transaction-level discrepancies |
| `GET /inventory/advanced?fromDate=&toDate=` | ABC/XYZ and movement metrics |
| `GET /customers/advanced?fromDate=&toDate=` | Customer RFM and cohort metrics |
| `GET /suppliers/advanced?fromDate=&toDate=` | Supplier lead-time and payment metrics |
| `GET /targets?fromDate=&toDate=` | Target, actual, variance, and forecast |
| `POST /targets` | Admin-only target configuration |
| `POST /scenarios` | Stateless hypothetical sales/expense simulation |

Scenario requests accept `fromDate`, `toDate`, and optional
`sellingPriceChangePercent`, `salesVolumeChangePercent`, and
`expenseChangePercent` values from -100 through 1000. Their output is marked
`HYPOTHETICAL`; the endpoint only reads ERP data.

Target `metricKey` values are `MONTHLY_SALES`, `PRODUCT_SALES`,
`CATEGORY_REVENUE`, and `EXPENSE_BUDGET`. Product and category metrics require
their corresponding IDs. Active target scopes cannot be duplicated.

## Formula definitions and limitations

* **Data-quality score:** `max(0, 100 * (records checked - distinct affected
  records) / records checked)`. It checks product, customer, supplier, sales,
  payment, return, and purchase records. Backdated sale and purchase dates are
  allowed; dates after the current business date and payment dates before their
  invoice date are flagged. Issue status and notes are stored in
  `report_issue_resolutions`; detected transactions are never modified.
* **Reconciliation:** differences are emitted with record references and source
  values. Sales paid amounts and return refund statuses are compared with their
  posted payment/refund rows. Purchase payments are checked against balances
  after returns and applied credits; supplier credit applications back to the
  same purchase already reduced by their source return are flagged as possible
  double counting. Cash sessions and source-linked cash movements are checked
  against their source records; return/refund and supplier-credit usage totals
  are compared. Daily closings are checked against sales, purchases, expenses,
  sales returns, refunds, and closed register sessions. Refunds and supplier
  credits are not separately added to invoice totals. Purchases have no stored
  supplier outstanding balance, so the report cannot independently compare a
  persisted supplier ledger balance with an invoice balance.
* **ABC inventory:** products are ranked by positive net selected-period
  `sales_products` taxable amount less accepted return taxable amount (excluding
  sales tax); A is the first 80% cumulative share, B the next 15%, and C the
  remainder. **XYZ inventory:** population coefficient of variation of monthly
  net units, normalizing each partial month to a 30.4375-day month (X <= 0.50,
  Y <= 1.00, Z > 1.00 or no demand). Movement class uses the last completed
  sale in-period: <=30 days is fast, >90 days is slow, none is
  `NO_SALES_IN_PERIOD`; this is not lot-level dead-stock or stock-age
  calculation. Days since movement is explicitly movement inactivity, not stock
  age. Product cost is never inferred.
* **Customer RFM:** completed-sale recency (days from report end), distinct
  invoice frequency, and taxable sales value less accepted return taxable
  amount are separately
  ranked into five quintiles (5 is highest). Cohort month is the first
  completed-sale month in available history. Lifetime value, retention
  prediction, churn, and next-purchase prediction are not emitted.
* **Supplier analytics:** lead time is `received_at - purchase_date` in
  business-timezone calendar days; variability is population standard
  deviation. Payment coverage is purchase payments plus credit applications
  against those invoices divided by received invoice total. Average days to
  first payment is measured from invoice date. These measures are as of the
  selected period end. Return rate is purchase-return total divided by received
  invoice total. Concentration is the supplier's share of selected-period
  received purchase value. Promised
  delivery dates and defect reasons are not stored, so delivery timeliness and
  defect rates are not estimated.
* **Targets:** variance is `actual - target`; remaining is
  `max(target - actual, 0)`. Achievement is `actual / target * 100` (null when
  target is zero). The displayed forecast is a straight-line estimate:
  actual-to-date divided by elapsed calendar days, multiplied by total
  target-period calendar days.
  Monthly and product/category sales actuals exclude sales tax; expense-budget
  actuals sum active expense records.
* **Scenario:** simulated revenue is baseline completed-sale revenue multiplied
  by `(1 + price change) * (1 + volume change)`. Simulated expenses are baseline
  active expenses multiplied by `(1 + expense change)`. These are independent
  assumptions and the result does not write ERP records.

Gross margin, product profitability, GMROI, inventory turnover, EOQ, and
valuation-based targets are intentionally unavailable: the schema does not
provide a reliable transaction-level COGS/valuation model. Email scheduling,
XLSX/background exports, custom formula reports, and AI predictions are also
not implemented; the repository currently has no reporting scheduler, email
delivery integration, export job store, approved formula engine, or evaluated
prediction model. Do not treat these APIs as completing those capabilities.
