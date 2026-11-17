# Auto Parts ERP AI Assistant: Business Question Coverage Matrix

> [!IMPORTANT]
> **DATABASE & REPOSITORY VERIFICATION REQUIREMENT (MANDATORY)**:
> All proposed database table names, column identifiers, foreign-key joins, financial aggregations, and formula mappings in this matrix are **provisional architectural targets**.
>
> This matrix does **NOT** serve as proof that every listed mapping, column, or calculation currently exists in the database. Before implementing any domain tool in Stage 2, the exact PostgreSQL tables, column names, constraints, existing service methods, and repository queries must be formally inspected and verified against the live PostgreSQL schema and the codebase.
>
> Under no circumstances may an agent or engineer invent tables, columns, joins, or accounting calculations. If a proposed capability lacks underlying database or business-rule support, it must be flagged and documented rather than fabricated.
>
> **Financial & Cost Accounting Limitation (COGS & Gross Profit)**:
> The current ERP data model does not record transaction-level historical purchase costs for sold items (`sales_products` records selling price, and `products.purchase_price` is mutable). Conforming to `AdvancedReportServiceImpl`, certified Cost of Goods Sold (COGS), gross profit, and margin calculations are unavailable. The assistant must state this system limitation rather than computing or estimating profit metrics. Verified metrics are Sales Revenue (`sales` where `status = 'COMPLETED'`), Purchase Total (`purchases` where `status = 'RECEIVED'`), Customer/Supplier Ledgers, and Active Expenses.

This document defines the supported business question scope across all 10 ERP domains. It serves as the specification for intent classification, entity extraction, parameter validation, role requirements, and expected empty/error behavior.

---

## General Interpretation & Entity Resolution Rules

1. **Entity Extraction Patterns**:
   * **Product Code**: Matches `GAP-######` or alphanumeric code with letters and digits (`[A-Z0-9]+-[A-Z0-9]+`).
   * **Part Number**: OEM or aftermarket part number (e.g. `2410-019`, `BP-5542`).
   * **Purchase Number**: Matches `PUR-######`.
   * **Sales Invoice Number**: Matches `INV-######`.
   * **Customer Code**: Matches `CUST-######`.
   * **Phone Number**: Normalized 10-digit number (`[6-9]\d{9}`).
   * **Supplier GSTIN**: 15-character alphanumeric GSTIN.
   * **Calendar Dates**: Explicit `YYYY-MM-DD` format.
   * **Relative Dates**: Resolved to inclusive `Asia/Kolkata` date ranges:
     * "today" -> `current_date` to `current_date`
     * "yesterday" -> `current_date - 1` to `current_date - 1`
     * "this month" -> 1st of current month to `current_date`
     * "last month" -> 1st of previous month to last day of previous month
     * "last week" / "past 7 days" -> `current_date - 7` to `current_date`
2. **Ambiguity & Disambiguation Rules**:
   * If a customer or supplier query matches multiple records (e.g., "Sharma"), the assistant lists up to 5 matching names with city/phone and asks: *"Which customer did you mean?"*
   * If a follow-up refers to "it" or "the invoice" and multiple entities were discussed in the immediate conversation context, the assistant asks for clarification rather than silently guessing.
3. **Strict Read-Only Guardrails**:
   * Transactional mutations ("create purchase", "record payment", "change stock") are intercepted and explained as unsupported in read-only mode.

---

## Domain A: Product Management

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Product Search** | • "Find brake pads for Swift"<br>• "Search oil filters"<br>• "Do we have spark plugs?" | `query` (string) | `products` (WHERE active = TRUE AND LOWER(name/product_code/part_no) LIKE %query%) | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Empty: *"No matching products found."* Caps at 20 rows. |
| **Product Details**| • "Show details for GAP-000001"<br>• "Specs for part number BP-102" | `productCode` or `partNo` | `products` JOIN `product_categories` JOIN `product_brands` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | If missing: *"Product not found."* Multiple: ask for exact code. |
| **Product Comparison** | • "Compare GAP-000001 and GAP-000002"<br>• "Differences between BP-101 and BP-102" | 2 to 5 `productCodes`/`partNos` | `products` WHERE product_code IN (...) | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Validates 2–5 items. Explicitly reports identifiers not found. |
| **Product Count** | • "How many active products do we have?"<br>• "Total product count" | None | `SELECT COUNT(*) FROM products WHERE active = TRUE` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Returns integer count. |
| **Category / Brand Lookup** | • "Show all products in Brakes category"<br>• "List Bosch brand products" | `categoryName` or `brandName` | `products` JOIN `product_categories` / `product_brands` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | If category/brand invalid: *"Category not found."* |

---

## Domain B: Inventory Management

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Current Stock** | • "What is the stock of GAP-000001?"<br>• "How many units of BP-102 are available?" | `productCode` or `partNo` | `products.qty`, `unit`, `minimum_stock` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Reports available quantity and whether stock is below minimum. |
| **Low-Stock Alert**| • "Which products are running low?"<br>• "Show items below minimum stock"<br>• "What needs restocking?" | None | `products` WHERE qty <= minimum_stock AND active = TRUE ORDER BY qty ASC | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Empty: *"There are currently no active products below minimum stock."* |
| **Movement History**| • "Show movement history for GAP-000001"<br>• "Stock in and out for part 2410-019" | `productCode`, limit (default 10, max 50) | `inventory_movements` JOIN `products` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Breaks down IN/OUT, movement type, date, reference. |
| **Movement Summary**| • "Movement summary for GAP-000001"<br>• "How many units of GAP-000001 were sold?" | `productCode` | `inventory_movements` GROUP BY movement_type | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Summarizes total volume moved and net quantity change. |
| **Date-Range Movements** | • "Movements for GAP-000001 from 2026-10-01 to 2026-10-15"<br>• "GAP-000001 movements last month" | `productCode`, `startDate`, `endDate` | `inventory_movements` WHERE created_at >= start AND created_at < end+1 | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Validates start <= end. Timezone: `Asia/Kolkata`. |
| **Stock Adjustments**| • "List recent stock adjustments"<br>• "Adjustments for GAP-000001" | Optional `productCode`, limit | `inventory_adjustments` JOIN `inventory_movements` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows adjustment reason, notes, quantity change, date. |

---

## Domain C: Purchase Management

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Purchase Order Lookup** | • "Show purchase PUR-000050"<br>• "Details of purchase order 50" | `purchaseNumber` (`PUR-######`) | `purchases` JOIN `suppliers` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Empty: *"Purchase number PUR-000050 was not found."* |
| **Supplier Invoice Lookup** | • "Find supplier invoice INV-BOSCH-99"<br>• "Invoice 99 from Bosch" | `supplierInvoiceNo`, optional `supplierName` | `purchases` WHERE LOWER(supplier_invoice_no) = LOWER(...) | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Disambiguates if multiple suppliers used same invoice string. |
| **Purchases by Supplier** | • "Show purchases from ABC Spares"<br>• "What did we buy from Bosch last month?" | `supplierName`, optional date range, status | `purchases` JOIN `suppliers` WHERE supplier_id = ? | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Lists date, purchase number, invoice no, status, grand total. |
| **Purchases by Status** | • "List draft purchases"<br>• "Show pending received purchases" | `status` (`DRAFT`, `RECEIVED`, `CANCELLED`) | `purchases` WHERE status = ? | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Caps at 50 records. |
| **Purchase Line Items** | • "What items were in PUR-000050?"<br>• "Show product lines for invoice INV-123" | `purchaseNumber` or `supplierInvoiceNo` | `purchase_products` JOIN `products` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows productCode, name, quantity, purchase_price, line_total. |
| **Last Purchase Price** | • "What was the last purchase price of GAP-000001?"<br>• "When did we last buy BP-102 and at what rate?" | `productCode` or `partNo` | `purchase_products` JOIN `purchases` ORDER BY purchase_date DESC LIMIT 1 | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows last price, date, supplier, purchase number. |
| **Outstanding Purchase Balance** | • "How much is pending to supplier ABC?"<br>• "Outstanding purchase payments" | Optional `supplierName` | `SupplierLedgerRepository.getSummary` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows total billed, total paid, applied credits, net balance. |

---

## Domain D: Sales Management

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Invoice Lookup** | • "Show sales invoice INV-000100"<br>• "Details for bill INV-000100" | `invoiceNumber` (`INV-######`) | `sales` JOIN `customers` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows date, customer, subtotal, tax, grand total, paid, balance. |
| **Sales by Customer** | • "Show sales for Sharma Enterprises"<br>• "Invoices for phone 9876543210" | `customerName` or `phone` | `sales` JOIN `customers` WHERE customer_id = ? | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Lists recent sales with status and balance. |
| **Sales by Date Range**| • "How much did we sell yesterday?"<br>• "Sales from 2026-11-01 to 2026-11-10" | `startDate`, `endDate` | `sales` WHERE sale_date >= start AND sale_date <= end | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Summarizes count, total revenue, tax collected, pending balance. |
| **Sales Product Lines**| • "What was sold in invoice INV-000100?" | `invoiceNumber` | `sales_products` JOIN `sales` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows productCode, name, qty, unitPrice, discount, lineTotal. |
| **Unpaid / Partially Paid Sales** | • "List unpaid sales invoices"<br>• "Which invoices have pending balance?" | Status / paymentStatus (`UNPAID`, `PARTIALLY_PAID`) | `sales` WHERE payment_status != 'PAID' AND status = 'COMPLETED' | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Sorted by balance descending, capped at 50 rows. |

---

## Domain E: Customer Management

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Customer Search** | • "Find customer Sharma"<br>• "Search customer with phone 9876543210" | `customerName`, `phone`, `customerCode` | `customers` (WHERE LOWER(customer_name) LIKE %..% OR phone = ..) | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows code, name, phone, city, active status. |
| **Customer Balance** | • "How much does Sharma owe us?"<br>• "Pending balance for customer CUST-000012" | `customerName` or `customerCode` | `CustomerLedgerRepository.getSummary` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Reports opening balance, total debits, total credits, net balance. |
| **Customer Ledger** | • "Show ledger for customer Sharma"<br>• "Customer statement for Sharma from Oct 1" | `customerName`, `fromDate`, `toDate` | `CustomerLedgerRepository.findEntries` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Lists date, reference (INV/PMT/RET), debit, credit, running balance. |
| **Customer Sales Summary** | • "Total sales for customer Sharma this year"<br>• "Top purchases by Sharma" | `customerName`, date range | `sales` GROUP BY customer_id | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Summarizes completed invoice count and net revenue. |

---

## Domain F: Supplier Management

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Supplier Search** | • "Find supplier Bosch"<br>• "Contact details for ABC Auto Parts" | `supplierName`, `phone`, `gstin` | `suppliers` (WHERE LOWER(supplier_name) LIKE %..%) | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows name, contact person, phone, email, GSTIN, payment terms. |
| **Supplier Balance** | • "How much do we owe Bosch?"<br>• "Outstanding balance for supplier ABC" | `supplierName` | `SupplierLedgerRepository.getSummary` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Reports total purchases, payments made, credits applied, balance. |
| **Supplier Ledger** | • "Show ledger for Bosch"<br>• "Supplier statement for Bosch last month" | `supplierName`, `fromDate`, `toDate` | `SupplierLedgerRepository.findEntries` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Lists invoice purchases, payment vouchers, returns, running balance. |
| **Supplier Lead Time & Analytics** | • "What is the average lead time for Bosch?"<br>• "Supplier return rate for ABC" | `supplierName`, date range | `AdvancedReportRepository.getSupplierAnalytics` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows lead time days, variability, return rate, payment coverage. |

---

## Domain G: Returns and Credits

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Sales Returns** | • "Show returns for sales invoice INV-000100"<br>• "List recent sales returns" | Optional `invoiceNumber`, limit | `sales_returns` JOIN `sales` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows return date, reason, return amount, refund status. |
| **Customer Refunds**| • "Show refunds for sales return"<br>• "List cash refunds given today" | Optional `date`, `paymentMethod` | `sales_refunds` JOIN `sales_returns` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows refund amount, payment method (CASH/UPI/CARD), date. |
| **Purchase Returns**| • "Show purchase return PR-000005"<br>• "Returns for purchase PUR-000050" | `returnNumber` or `purchaseNumber` | `purchase_returns` JOIN `purchases` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows return number, purchase number, return amount, reason. |
| **Supplier Credits**| • "List active supplier credit notes"<br>• "Credits available from Bosch" | Optional `supplierName` | `supplier_credits` JOIN `purchase_returns` JOIN `purchases` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows credit note number, original return, amount, applied status. |

---

## Domain H: Payments and Financial Information

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cash Register Status**| • "Is cash register REG-1 currently open?"<br>• "Opening balance for today's cash session" | `registerCode` | `cash_register_sessions` JOIN `cash_registers` WHERE status = 'OPEN' | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows register name, opened_by, opened_at, opening_balance. |
| **Cash Movements** | • "Show cash movements for active session"<br>• "List cash payouts today" | Optional `movementType` (`IN`/`OUT`) | `cash_register_movements` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Shows source type (SALE_PAYMENT, EXPENSE, MANUAL), amount, time. |
| **Expenses by Date / Category** | • "Show expenses for this month"<br>• "How much did we spend on Rent?" | Optional `categoryName`, date range | `expenses` JOIN `expense_categories` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows title, category, amount, payment method, date. |
| **Daily Closing Summary** | • "Show daily closing for 2026-11-15"<br>• "Yesterday's daily closing figures" | `businessDate` (`YYYY-MM-DD`) | `daily_closings` JOIN `users` | `ROLE_MANAGER`, `ROLE_ADMIN` (`CASHIER` blocked) | Shows sales totals, payments collected (cash/UPI/card), expenses. |

---

## Domain I: Reports and Analytics

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Backend Table / Query | Role Required | Empty / Error Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Dashboard Summary** | • "Show executive dashboard summary"<br>• "Business summary for October 2026" | `fromDate`, `toDate` | `ReportRepository.getDashboardSummary` | `ROLE_MANAGER`, `ROLE_ADMIN` | Reports sales total, purchase total, expense total, net collections. |
| **Inventory ABC/XYZ** | • "Which products are Category A in ABC analysis?"<br>• "Fast moving products in the last 30 days" | `fromDate`, `toDate` | `AdvancedReportRepository.getInventoryAnalytics` | `ROLE_MANAGER`, `ROLE_ADMIN` | Categorizes revenue share (A: 80%, B: 15%, C: 5%) & movement frequency. |
| **Customer RFM Cohorts** | • "Show customer RFM segment report"<br>• "Who are our top customers by frequency and value?" | `fromDate`, `toDate` | `AdvancedReportRepository.getCustomerAnalytics` | `ROLE_MANAGER`, `ROLE_ADMIN` | Ranks recency, frequency, monetary quintiles (1-5). |
| **Financial Reconciliation** | • "Run financial reconciliation for last month"<br>• "Are there payment or cash register discrepancies?" | `fromDate`, `toDate` | `AdvancedReportRepository.getFinancialReconciliation` | `ROLE_ADMIN` (`MANAGER`/`CASHIER` blocked) | Checks posted payments vs. invoice totals and cash register discrepancies. |
| **Data Quality Audit**| • "What is our current data quality score?"<br>• "List unresolved data issues" | `fromDate`, `toDate` | `AdvancedReportRepository.getDataQualityReport` | `ROLE_ADMIN` (`MANAGER`/`CASHIER` blocked) | Reports percentage score and flagged transactions (backdated/future). |
| **Profit & Margin Inquiries** | • "What was our gross profit last month?"<br>• "Show profit margin for GAP-000001"<br>• "What is our COGS?" | None / date range | Not Supported (Schema Limitation) | `ROLE_MANAGER`, `ROLE_ADMIN` | Explains cleanly: *"Gross profit, margin, and Cost of Goods Sold (COGS) are not available: the current ERP data model does not record transaction-level historical purchase costs for sold items."* |

---

## Domain J: General ERP Assistance & Knowledge (RAG)

| Capability | Sample User Questions (Natural Variations) | Extracted Entities | Knowledge Retrieval Method | Role Required | Empty / Grounding Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Warranty Policy** | • "What is our warranty policy on car batteries?"<br>• "Warranty duration for brake pads" | Semantic keywords (`battery`, `warranty`) | RAG: `knowledge_chunks` (semantic vector search with FTS fallback) | Document required role (Public / Staff) | Cites document title and section: `[Source: Warranty Policy v1.2, §3.1]`. |
| **ERP Workflow SOPs**| • "How do I perform a daily cash closing?"<br>• "Procedure for recording a purchase return" | Semantic keywords (`procedure`, `steps`) | RAG: `knowledge_chunks` | Document required role (Staff / Manager) | Step-by-step instruction cited from official user manual. |
| **ERP Concept Guidance**| • "What is the difference between subtotal and taxable amount in GST?" | General ERP concepts | General Prompt synthesis / RAG | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Factual, professional explanation based on Indian GST accounting rules. |
| **Unsupported Mutations** | • "Create a purchase order for 100 filters"<br>• "Cancel invoice INV-000100" | Write verbs (`create`, `delete`, `cancel`) | Intercepted in Java nodes | All roles | Cleanly explains: *"I am a read-only ERP assistant. Transactions must be performed directly in the ERP UI."* |
