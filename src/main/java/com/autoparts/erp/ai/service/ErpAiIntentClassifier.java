package com.autoparts.erp.ai.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ErpAiIntentClassifier {

    private static final Pattern PRODUCT_IDENTIFIER = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_IDENTIFIER = Pattern.compile(
            "\\bPUR-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern INVOICE_IDENTIFIER = Pattern.compile(
            "\\bINV-\\d+(?:-\\d+)*\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_IDENTIFIER = Pattern.compile(
            "\\bCUS(?:T-)?\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "\\b\\d{10}\\b");

    private static final Pattern PRODUCT_COMPARISON_REQUEST = Pattern.compile(
            "\\b(?:compare|comparison|differences?\\s+between|versus|vs\\.?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private static final Pattern RESULT_LIMIT =
            Pattern.compile("\\b(?:last|latest|top|recent)\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECENT_MOVEMENTS = Pattern.compile(
            "\\b(?:recent|latest|last\\s+\\d+|show\\s+\\d+\\s+latest)\\b"
                    + ".*\\b(?:inventory\\s+)?(?:stock\\s+)?movements?\\b"
                    + "|\\b(?:recent|latest)\\s+(?:inventory\\s+)?activity\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ADJUSTMENT_REQUEST = Pattern.compile(
            "\\b(?:inventory\\s+adjustments?|stock\\s+adjustments?|adjustment\\s+(?:history|records?|logs?)|"
                    + "show\\s+adjustments?|list\\s+adjustments?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOVEMENT_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:movement\\s+summary|summary\\s+of\\s+(?:inventory\\s+)?movements?|"
                    + "movement\\s+totals?|stock\\s+movement\\s+summary|"
                    + "how many (?:units|items) (?:were )?(?:purchased|sold|adjusted))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOVEMENT_HISTORY_REQUEST = Pattern.compile(
            "\\b(?:movement\\s+history|movements?|stock\\s+movements?|inventory\\s+history|"
                    + "transaction\\s+(?:history|logs?)|stock\\s+(?:in|out)|inward|outward|"
                    + "in\\s*/\\s*out\\s+movements?|movement\\s+logs?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_COUNT_REQUEST = Pattern.compile(
            "\\b(?:how\\s+many|count(?:\\s+of)?|number\\s+of|total(?:\\s+number\\s+of)?)\\b"
                    + ".*\\bproducts?\\b|\\bproducts?\\b.*\\b(?:count|total|number)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTIVE_PRODUCTS_AVAILABLE = Pattern.compile(
            "\\bactive\\s+products?\\s+(?:are\\s+)?available\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOW_STOCK_REQUEST = Pattern.compile(
            "\\b(?:low\\s+stock|stock\\s+(?:is\\s+)?low|below\\s+(?:the\\s+)?minimum|"
                    + "under\\s+(?:the\\s+)?minimum|running\\s+low|need(?:s)?\\s+restock(?:ing)?|"
                    + "restock(?:ing)?|replenish(?:ment)?|out\\s+of\\s+stock)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_STOCK_REQUEST =
            Pattern.compile("\\b(?:stock|inventory|quantity|availability)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_DETAILS_REQUEST =
            Pattern.compile("\\b(?:details?|information|price|description|specifications?|part\\s+number)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_SEARCH_REQUEST =
            Pattern.compile("\\b(?:find|search|look\\s*up|lookup|show|display|list|get|tell\\s+me\\s+about)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERIC_STOCK_REQUEST =
            Pattern.compile("^(?:current\\s+)?(?:stock|inventory|quantity|availability)$",
                    Pattern.CASE_INSENSITIVE);

    // Stage 2C Regex Patterns: Purchase
    private static final Pattern PURCHASE_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:purchase\\s+summary|purchase\\s+totals?|total\\s+purchases?|procurement\\s+summary|how\\s+much\\s+(?:did\\s+we\\s+)?purchase)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_STATUS_REQUEST = Pattern.compile(
            "\\b(?:purchase\\s+status|status\\s+of\\s+purchase|order\\s+status)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:purchase\\s+details?|details?\\s+of\\s+purchase|view\\s+purchase|purchase\\s+info(?:rmation)?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:purchases?|purchase\\s+orders?)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2C Regex Patterns: Sales
    private static final Pattern SALES_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:sales?\\s+summary|sales?\\s+totals?|total\\s+sales?|revenue\\s+summary|total\\s+revenue|sales?\\s+revenue)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SALE_STATUS_REQUEST = Pattern.compile(
            "\\b(?:sales?\\s+status|invoice\\s+status|status\\s+of\\s+(?:sale|invoice)|payment\\s+status\\s+of\\s+invoice)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SALE_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:sales?\\s+details?|invoice\\s+details?|details?\\s+of\\s+(?:sale|invoice)|show\\s+invoice)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SALES_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:sales?|invoices?)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2C Regex Patterns: Customer
    private static final Pattern CUSTOMER_OUTSTANDING_REQUEST = Pattern.compile(
            "\\b(?:outstanding(?:\\s+balance)?|pending\\s+balance|customer\\s+balance|balance\\s+for\\s+customer|how\\s+much\\s+does\\s+.*\\s+owe)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_SALES_HISTORY_REQUEST = Pattern.compile(
            "\\b(?:customer\\s+sales?\\s+history|sales?\\s+history\\s+for\\s+customer|customer\\s+purchase\\s+history|invoices?\\s+for\\s+customer)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:customer\\s+details?|details?\\s+of\\s+customer|customer\\s+info(?:rmation)?|who\\s+is\\s+customer)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:customers?)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2C Regex Patterns: Supplier
    private static final Pattern SUPPLIER_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:supplier\\s+(?:purchase\\s+)?summary|supplier\\s+spend|spend\\s+summary|how\\s+much\\s+(?:did\\s+we\\s+)?buy\\s+from)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SUPPLIER_PURCHASE_HISTORY_REQUEST = Pattern.compile(
            "\\b(?:supplier\\s+purchase\\s+history|purchase\\s+history\\s+for\\s+supplier|purchases?\\s+from\\s+supplier|order\\s+history\\s+from)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SUPPLIER_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:supplier\\s+details?|details?\\s+of\\s+supplier|supplier\\s+info(?:rmation)?|contact\\s+(?:info|details)\\s+for\\s+supplier)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SUPPLIER_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:suppliers?|vendors?)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2D Regex Patterns: Returns
    private static final Pattern RETURN_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:returns?\\s+summary|returns?\\s+totals?|total\\s+returns?|summary\\s+of\\s+returns?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SALES_RETURN_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:sales?\\s+returns?\\s+details?|details?\\s+of\\s+sales?\\s+return|customer\\s+returns?\\s+details?|refund\\s+details?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SALES_RETURN_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:sales?\\s+returns?|customer\\s+returns?|customer\\s+refunds?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_RETURN_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:purchase\\s+returns?\\s+details?|details?\\s+of\\s+purchase\\s+return|supplier\\s+returns?\\s+details?|supplier\\s+credit\\s+details?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_RETURN_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:purchase\\s+returns?|supplier\\s+returns?|supplier\\s+credits?|debit\\s+notes?)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2D Regex Patterns: Financials
    private static final Pattern CASH_REGISTER_STATUS_REQUEST = Pattern.compile(
            "\\b(?:cash\\s+registers?\\s+(?:status|drawer|sessions?)|open\\s+(?:cash\\s+)?registers?|cash\\s+in\\s+drawer|drawer\\s+balance|active\\s+registers?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CASH_DISCREPANCIES_REQUEST = Pattern.compile(
            "\\b(?:cash\\s+(?:register\\s+)?discrepanc(?:y|ies)|register\\s+discrepanc(?:y|ies)|cash\\s+shortage|register\\s+surplus|cash\\s+differences?|session\\s+discrepanc(?:y|ies))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DAILY_CLOSING_REQUEST = Pattern.compile(
            "\\b(?:daily\\s+closing|business\\s+closing|day\\s+closing|end\\s+of\\s+day|closing\\s+report)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPENSE_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:expenses?\\s+summary|expenses?\\s+totals?|total\\s+expenses?|operating\\s+expenses?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPENSE_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:expenses?|spending|expenditures?)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2D Regex Patterns: Reports
    private static final Pattern COGS_PROFIT_REQUEST = Pattern.compile(
            "\\b(?:cogs|cost\\s+of\\s+goods\\s+sold|gross\\s+profit|gross\\s+margin|net\\s+margin|product\\s+profitability|profit\\s+margin)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KPI_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:kpi\\s+summary|business\\s+kpis?|executive\\s+summary|executive\\s+kpis?|key\\s+performance\\s+indicators?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVENTORY_ABC_XYZ_REQUEST = Pattern.compile(
            "\\b(?:abc\\s+analysis|abc/xyz|inventory\\s+abc|category\\s+a\\s+products?|top\\s+revenue\\s+products?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_RFM_REQUEST = Pattern.compile(
            "\\b(?:rfm\\s+analysis|customer\\s+rfm|customer\\s+segmentation|vip\\s+customers?|customer\\s+cohorts?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RECONCILIATION_DISCREPANCIES_REQUEST = Pattern.compile(
            "\\b(?:reconciliation\\s+discrepanc(?:y|ies)|ledger\\s+reconciliation|data\\s+quality\\s+(?:issues?|report)|cross-ledger)\\b",
            Pattern.CASE_INSENSITIVE);

    // Stage 2D Regex Patterns: Audit Logs
    private static final Pattern AUDIT_LOG_REQUEST = Pattern.compile(
            "\\b(?:audit\\s+logs?|system\\s+audit(?:\\s+trail)?|security\\s+logs?|recent\\s+audit\\s+events?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AI_TOOL_RUNS_REQUEST = Pattern.compile(
            "\\b(?:ai\\s+tool\\s+(?:runs?|logs?)|ai\\s+diagnostic(?:\\s+logs?)?|tool\\s+execution\\s+logs?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KNOWLEDGE_REQUEST = Pattern.compile(
            "\\b(?:warranty(?:\\s+policy)?|return\\s+policy|how\\s+to\\s+return|refund\\s+policy|"
                    + "sop|standard\\s+operating\\s+procedure|operating\\s+hours|shipping\\s+policy|"
                    + "core\\s+(?:exchange|deposit|return)\\s+policy|exchange\\s+policy|"
                    + "company\\s+policy|employee\\s+handbook|guidelines?|manuals?|"
                    + "policy\\s+(?:on|for|regarding)|terms\\s+(?:and|&)\\s+conditions)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> SEARCH_PREFIXES = List.of(
            Pattern.compile("^(?:(?:please|kindly)\\s+)+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:(?:can|could|would)\\s+you\\s+)+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:find|search(?:\\s+for)?|look\\s*up|lookup|show(?:\\s+me)?|display|list|get|check|tell\\s+me\\s+about|give\\s+me)\\s+",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:what(?:\\s+is|\\s+are|'s)|how\\s+much(?:\\s+stock)?|where\\s+can\\s+i\\s+find)\\s+",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:the\\s+)?(?:stock(?:\\s+level|\\s+quantity)?|inventory|(?:available\\s+)?quantity|details?|information|price|description|specifications?)\\b(?:\\s+(?:of|for|on|about))?\\s*",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:the\\s+)?(?:products?|items?|purchases?|sales?|invoices?|customers?|suppliers?)(?:\\s+(?:named|called|by\\s+name))?\\b\\s*",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:for|about|of|a|an|the)\\b\\s*", Pattern.CASE_INSENSITIVE));

    public ErpAiIntentResult classify(String message) {
        if (!StringUtils.hasText(message)) {
            return ErpAiIntentResult.clarification("Please tell me what ERP information you need.");
        }

        String normalized = message.toLowerCase(Locale.ROOT);

        if (isUnsupportedRequest(normalized)) {
            return new ErpAiIntentResult("UNSUPPORTED", extractSearchText(message));
        }

        // -------------------------------------------------------------
        // STAGE 2D: AUDIT LOGS & AI DIAGNOSTICS
        // -------------------------------------------------------------
        if (AUDIT_LOG_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("AUDIT_LOG_SEARCH", "", "", "", extractLimit(message), "");
        }

        if (AI_TOOL_RUNS_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("AI_TOOL_RUNS_LOG", "", "", "", extractLimit(message), "");
        }

        // -------------------------------------------------------------
        // STAGE 2D: REPORTS & BUSINESS ANALYTICS
        // -------------------------------------------------------------
        if (COGS_PROFIT_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("COGS_PROFIT_EXPLANATION", "");
        }

        if (KPI_SUMMARY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("KPI_SUMMARY", "", firstDate, secondDate, 50, "");
        }

        if (INVENTORY_ABC_XYZ_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("INVENTORY_ABC_ANALYSIS", "");
        }

        if (CUSTOMER_RFM_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("CUSTOMER_RFM_ANALYSIS", "");
        }

        if (RECONCILIATION_DISCREPANCIES_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("RECONCILIATION_DISCREPANCIES", "", firstDate, secondDate, 20, "");
        }

        // -------------------------------------------------------------
        // STAGE 2D: FINANCIALS & CASH MANAGEMENT
        // -------------------------------------------------------------
        if (CASH_REGISTER_STATUS_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("OPEN_CASH_REGISTERS", "");
        }

        if (CASH_DISCREPANCIES_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("CASH_DISCREPANCIES", "", firstDate, secondDate, 20, "");
        }

        if (DAILY_CLOSING_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String date = dates.find() ? dates.group() : "";
            if (date.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the business closing date in YYYY-MM-DD format.");
            }
            return new ErpAiIntentResult("DAILY_CLOSING", date);
        }

        if (EXPENSE_SUMMARY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("EXPENSE_SUMMARY", "", firstDate, secondDate, 50, "");
        }

        if (EXPENSE_SEARCH_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            if (!firstDate.isEmpty() && !secondDate.isEmpty()) {
                return validateDateRange("EXPENSE_SUMMARY", "", firstDate, secondDate);
            }
            String search = extractSearchText(message);
            if (StringUtils.hasText(search) && !search.equalsIgnoreCase("expenses") && !search.equalsIgnoreCase("expense")) {
                return new ErpAiIntentResult("EXPENSES_BY_CATEGORY", search, firstDate, secondDate, 20, "");
            }
            return new ErpAiIntentResult("EXPENSE_SUMMARY", "", firstDate, secondDate, 50, "");
        }

        // -------------------------------------------------------------
        // STAGE 2D: RETURNS & CREDITS
        // -------------------------------------------------------------
        if (RETURN_SUMMARY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("RETURN_SUMMARY", "", firstDate, secondDate, 50, "");
        }

        if (PURCHASE_RETURN_DETAILS_REQUEST.matcher(message).find()) {
            String purchaseId = extractPurchaseNumber(message);
            String identifier = purchaseId != null ? purchaseId : extractSearchText(message);
            if (identifier.isEmpty() || identifier.equalsIgnoreCase("purchase return") || identifier.equalsIgnoreCase("purchase returns")) {
                return ErpAiIntentResult.clarification("Please provide the purchase return number or purchase order number.");
            }
            return new ErpAiIntentResult("PURCHASE_RETURN_DETAILS", identifier);
        }

        if (PURCHASE_RETURN_SEARCH_REQUEST.matcher(message).find()) {
            String search = extractSearchText(message);
            return new ErpAiIntentResult("PURCHASE_RETURN_SEARCH", search);
        }

        if (SALES_RETURN_DETAILS_REQUEST.matcher(message).find()) {
            String invoiceId = extractInvoiceNumber(message);
            String identifier = invoiceId != null ? invoiceId : extractSearchText(message);
            if (identifier.isEmpty() || identifier.equalsIgnoreCase("sales return") || identifier.equalsIgnoreCase("sales returns")) {
                return ErpAiIntentResult.clarification("Please provide the invoice number to view sales return details.");
            }
            return new ErpAiIntentResult("SALES_RETURN_DETAILS", identifier);
        }

        if (SALES_RETURN_SEARCH_REQUEST.matcher(message).find()) {
            String search = extractSearchText(message);
            return new ErpAiIntentResult("SALES_RETURN_SEARCH", search);
        }

        // -------------------------------------------------------------
        // 1. PURCHASE INTENTS (Stage 2C)
        // -------------------------------------------------------------
        String purchaseId = extractPurchaseNumber(message);

        if (PURCHASE_SUMMARY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("PURCHASE_SUMMARY", "", firstDate, secondDate, 50, "");
        }

        if (PURCHASE_STATUS_REQUEST.matcher(message).find() || (purchaseId != null && normalized.contains("status"))) {
            if (purchaseId == null) {
                return ErpAiIntentResult.clarification("Please provide the purchase number to check its status.");
            }
            return new ErpAiIntentResult("PURCHASE_STATUS", purchaseId);
        }

        if (PURCHASE_DETAILS_REQUEST.matcher(message).find() || (purchaseId != null && !normalized.contains("movements"))) {
            if (purchaseId == null) {
                String search = extractSearchText(message);
                if (!search.isEmpty()) {
                    return new ErpAiIntentResult("PURCHASE_DETAILS", search);
                }
                return ErpAiIntentResult.clarification("Please provide the purchase number or supplier invoice number.");
            }
            return new ErpAiIntentResult("PURCHASE_DETAILS", purchaseId);
        }

        if (PURCHASE_SEARCH_REQUEST.matcher(message).find() && !normalized.contains("movement")
                && !normalized.contains("supplier purchase") && !(normalized.contains("supplier") && normalized.contains("history"))) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";

            if (!firstDate.isEmpty() && !secondDate.isEmpty()) {
                return validateDateRange("PURCHASE_DATE_RANGE", "", firstDate, secondDate);
            }

            if (!normalized.contains("history") && (normalized.contains("from supplier") || normalized.contains("for supplier") || normalized.contains("by supplier"))) {
                String supplierQuery = extractEntityQuery(message, "supplier");
                if (supplierQuery.isEmpty()) {
                    return ErpAiIntentResult.clarification("Please provide the supplier name to search purchases.");
                }
                return new ErpAiIntentResult("PURCHASES_BY_SUPPLIER", supplierQuery);
            }

            String search = extractSearchText(message);
            if (StringUtils.hasText(search) && !search.equalsIgnoreCase("purchases") && !search.equalsIgnoreCase("purchase")) {
                return new ErpAiIntentResult("PURCHASE_SEARCH", search);
            }
            return new ErpAiIntentResult("PURCHASE_SEARCH", "");
        }

        // -------------------------------------------------------------
        // 2. CUSTOMER INTENTS
        // -------------------------------------------------------------
        String customerId = extractCustomerCode(message);
        if (customerId == null) {
            customerId = extractPhoneNumber(message);
        }

        if (CUSTOMER_OUTSTANDING_REQUEST.matcher(message).find()) {
            String query = customerId != null ? customerId : extractEntityQuery(message, "customer");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (query.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the customer name, phone, or customer code.");
            }
            return new ErpAiIntentResult("CUSTOMER_OUTSTANDING", query);
        }

        if (CUSTOMER_SALES_HISTORY_REQUEST.matcher(message).find()) {
            String query = customerId != null ? customerId : extractEntityQuery(message, "customer");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (query.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the customer name, phone, or customer code.");
            }
            return new ErpAiIntentResult("CUSTOMER_SALES_HISTORY", query);
        }

        if (CUSTOMER_DETAILS_REQUEST.matcher(message).find()) {
            String query = customerId != null ? customerId : extractEntityQuery(message, "customer");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (query.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the customer name, phone, or customer code.");
            }
            return new ErpAiIntentResult("CUSTOMER_DETAILS", query);
        }

        if (CUSTOMER_SEARCH_REQUEST.matcher(message).find()
                && !normalized.contains("sale") && !normalized.contains("sales") && !normalized.contains("invoice")) {
            String query = customerId != null ? customerId : extractEntityQuery(message, "customer");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (StringUtils.hasText(query) && !query.equalsIgnoreCase("customer") && !query.equalsIgnoreCase("customers")) {
                return new ErpAiIntentResult("CUSTOMER_SEARCH", query);
            }
            return ErpAiIntentResult.clarification("Please provide a customer name, phone number, or customer code.");
        }

        // -------------------------------------------------------------
        // 3. SALES INTENTS
        // -------------------------------------------------------------
        String invoiceId = extractInvoiceNumber(message);

        if (SALES_SUMMARY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";
            return new ErpAiIntentResult("SALES_SUMMARY", "", firstDate, secondDate, 50, "");
        }

        if (SALE_STATUS_REQUEST.matcher(message).find() || (invoiceId != null && normalized.contains("status"))) {
            if (invoiceId == null) {
                return ErpAiIntentResult.clarification("Please provide the invoice number to check sale status.");
            }
            return new ErpAiIntentResult("SALE_STATUS", invoiceId);
        }

        if (SALE_DETAILS_REQUEST.matcher(message).find() || (invoiceId != null && !normalized.contains("movement"))) {
            if (invoiceId == null) {
                String search = extractSearchText(message);
                if (!search.isEmpty()) {
                    return new ErpAiIntentResult("SALE_DETAILS", search);
                }
                return ErpAiIntentResult.clarification("Please provide the invoice number to view sales details.");
            }
            return new ErpAiIntentResult("SALE_DETAILS", invoiceId);
        }

        if (SALES_SEARCH_REQUEST.matcher(message).find() && !normalized.contains("movement") && !KNOWLEDGE_REQUEST.matcher(normalized).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";

            if (!firstDate.isEmpty() && !secondDate.isEmpty()) {
                return validateDateRange("SALES_DATE_RANGE", "", firstDate, secondDate);
            }

            if (normalized.contains("customer") || normalized.contains("for customer") || normalized.contains("to customer")) {
                String customerQuery = extractEntityQuery(message, "customer");
                if (customerQuery.isEmpty()) {
                    return ErpAiIntentResult.clarification("Please provide the customer name or phone number.");
                }
                return new ErpAiIntentResult("SALES_BY_CUSTOMER", customerQuery);
            }

            String search = extractSearchText(message);
            if (StringUtils.hasText(search) && !search.equalsIgnoreCase("sales") && !search.equalsIgnoreCase("sale")
                    && !search.equalsIgnoreCase("invoices") && !search.equalsIgnoreCase("invoice")) {
                return new ErpAiIntentResult("SALES_SEARCH", search);
            }
            return new ErpAiIntentResult("SALES_SEARCH", "");
        }

        // -------------------------------------------------------------
        // 4. SUPPLIER INTENTS
        // -------------------------------------------------------------
        if (SUPPLIER_SUMMARY_REQUEST.matcher(message).find()) {
            String query = extractEntityQuery(message, "supplier");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (query.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the supplier name to view procurement summary.");
            }
            return new ErpAiIntentResult("SUPPLIER_SUMMARY", query);
        }

        if (SUPPLIER_PURCHASE_HISTORY_REQUEST.matcher(message).find()) {
            String query = extractEntityQuery(message, "supplier");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (query.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the supplier name to view purchase history.");
            }
            return new ErpAiIntentResult("SUPPLIER_PURCHASE_HISTORY", query);
        }

        if (SUPPLIER_DETAILS_REQUEST.matcher(message).find()) {
            String query = extractEntityQuery(message, "supplier");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (query.isEmpty()) {
                return ErpAiIntentResult.clarification("Please provide the supplier name to retrieve supplier details.");
            }
            return new ErpAiIntentResult("SUPPLIER_DETAILS", query);
        }

        if (SUPPLIER_SEARCH_REQUEST.matcher(message).find()) {
            String query = extractEntityQuery(message, "supplier");
            if (query.isEmpty()) {
                query = extractSearchText(message);
            }
            if (StringUtils.hasText(query) && !query.equalsIgnoreCase("supplier") && !query.equalsIgnoreCase("suppliers")
                    && !query.equalsIgnoreCase("vendor") && !query.equalsIgnoreCase("vendors")) {
                return new ErpAiIntentResult("SUPPLIER_SEARCH", query);
            }
            return ErpAiIntentResult.clarification("Please provide a supplier name, contact person, or phone number.");
        }

        // -------------------------------------------------------------
        // 5. PRODUCT & INVENTORY INTENTS (PRESERVED STAGE 2B)
        // -------------------------------------------------------------
        String identifier = extractIdentifier(message);

        if (PRODUCT_COMPARISON_REQUEST.matcher(message).find()) {
            List<String> identifiers = extractIdentifiers(message);
            if (identifiers.size() < 2) {
                return ErpAiIntentResult.clarification(
                        "Please provide at least two product codes or part numbers to compare.");
            }
            if (identifiers.size() > 5) {
                return ErpAiIntentResult.clarification(
                        "Please compare no more than five products at a time.");
            }
            return new ErpAiIntentResult(
                    "PRODUCT_COMPARISON", identifiers.get(0), "", "", null, "", identifiers);
        }

        if (RECENT_MOVEMENTS.matcher(message).find()) {
            int limit = extractLimit(message);
            return new ErpAiIntentResult("RECENT_INVENTORY_MOVEMENTS", "", "", "", limit, "");
        }

        if (ADJUSTMENT_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("INVENTORY_ADJUSTMENTS", identifier, "", "", 50, "");
        }

        if (MOVEMENT_SUMMARY_REQUEST.matcher(message).find()) {
            if (identifier.isEmpty()) {
                return ErpAiIntentResult.clarification(
                        "Please provide the product code or part number to retrieve its movement summary.");
            }
            return new ErpAiIntentResult("PRODUCT_MOVEMENT_SUMMARY", identifier);
        }

        if (MOVEMENT_HISTORY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";

            if (!firstDate.isEmpty() || !secondDate.isEmpty()
                    || normalized.contains(" from ") || normalized.contains(" between ")) {
                if (identifier.isEmpty()) {
                    return ErpAiIntentResult.clarification(
                            "Please provide the product code or part number for the date-range movement search.");
                }
                if (firstDate.isEmpty() || secondDate.isEmpty()) {
                    return ErpAiIntentResult.clarification(
                            "Please provide both start and end dates in YYYY-MM-DD format.");
                }
                return validateDateRange("PRODUCT_MOVEMENTS_BY_DATE_RANGE", identifier, firstDate, secondDate);
            }

            if (identifier.isEmpty()) {
                return ErpAiIntentResult.clarification(
                        "Please provide the product code or part number to retrieve movement history.");
            }
            return new ErpAiIntentResult("PRODUCT_MOVEMENT_HISTORY", identifier);
        }

        String searchText = extractSearchText(message);
        boolean hasSearchText = StringUtils.hasText(searchText);

        if (LOW_STOCK_REQUEST.matcher(normalized).find()) {
            return new ErpAiIntentResult("LOW_STOCK_PRODUCTS", "");
        }
        if (isProductCountRequest(normalized)) {
            return new ErpAiIntentResult("COUNT_PRODUCTS", "");
        }
        if (KNOWLEDGE_REQUEST.matcher(normalized).find() && !isLiveStockQuery(normalized)) {
            String query = hasSearchText ? searchText : message.trim();
            return new ErpAiIntentResult("KNOWLEDGE", query);
        }
//        if (PRODUCT_STOCK_REQUEST.matcher(normalized).find()) {
//            return hasSearchText && !GENERIC_STOCK_REQUEST.matcher(searchText).matches()
//                    ? new ErpAiIntentResult("PRODUCT_STOCK", searchText)
//                    : ErpAiIntentResult.clarification(
//                            "Please provide the product code or part number to check current stock.");
//        }
        if (PRODUCT_STOCK_REQUEST.matcher(normalized).find()) {

            if (!identifier.isEmpty()) {
                return new ErpAiIntentResult("PRODUCT_STOCK", identifier);
            }

            return ErpAiIntentResult.clarification(
                    "Please provide the product code or part number "
                            + "to check current stock."
            );
        }
        if (PRODUCT_DETAILS_REQUEST.matcher(normalized).find()) {
            return hasSearchText
                    ? new ErpAiIntentResult("PRODUCT_DETAILS", searchText)
                    : ErpAiIntentResult.clarification(
                            "Please provide the product code or part number to retrieve product details.");
        }
        if (PRODUCT_SEARCH_REQUEST.matcher(normalized).find()
                || PRODUCT_IDENTIFIER.matcher(message).find()) {
            return hasSearchText
                    ? new ErpAiIntentResult("PRODUCT_SEARCH", searchText)
                    : ErpAiIntentResult.clarification("Please provide a product name, product code, or part number.");
        }
        return new ErpAiIntentResult("GENERAL", searchText);
    }

    private ErpAiIntentResult validateDateRange(String intent, String identifier, String firstDate, String secondDate) {
        try {
            LocalDate from = LocalDate.parse(firstDate);
            LocalDate to = LocalDate.parse(secondDate);
            if (to.isBefore(from)) {
                return ErpAiIntentResult.clarification("The end date must be on or after the start date.");
            }
        } catch (DateTimeParseException ex) {
            return ErpAiIntentResult.clarification("Please provide valid dates in YYYY-MM-DD format.");
        }
        return new ErpAiIntentResult(intent, identifier, firstDate, secondDate, 50, "");
    }

    private int extractLimit(String message) {
        Matcher matcher = RESULT_LIMIT.matcher(message);
        if (!matcher.find()) {
            return 10;
        }
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(matcher.group(1))));
        } catch (NumberFormatException ex) {
            return 10;
        }
    }

    private boolean isProductCountRequest(String message) {
        return PRODUCT_COUNT_REQUEST.matcher(message).find()
                || ACTIVE_PRODUCTS_AVAILABLE.matcher(message).find();
    }

    private boolean isLiveStockQuery(String message) {
        return (message.contains("how many") || message.contains("count") || message.contains("available"))
                && (message.contains("in stock") || message.contains("inventory"));
    }

    private boolean isUnsupportedRequest(String message) {
        if (CASH_REGISTER_STATUS_REQUEST.matcher(message).find()) {
            return false;
        }
        if (Pattern.compile("\\b(?:named|called|create|make|record|add|update|delete|generate|register|process|open|close|post)\\s+(?:a\\s+)?(?:new\\s+)?(?:purchase|sale|sales|invoice|customer|supplier|vendor|return|refund|credit|expense|register|session|closingmatching|containing|with)\\b", Pattern.CASE_INSENSITIVE).matcher(message).find()) {
            return true;
        }
        return containsAny(message,
                "create purchase", "create sale", "create sales", "create invoice", "generate invoice",
                "make purchase", "make sale", "make sales", "record purchase", "record sale", "record sales",
                "add customer", "delete customer", "add supplier", "delete supplier",
                "create customer", "create supplier", "update customer", "update supplier",
                "process return", "process refund", "issue credit note", "create expense", "add expense",
                "record expense", "perform daily closing", "post closing");
    }

    private String extractPurchaseNumber(String message) {
        Matcher matcher = PURCHASE_IDENTIFIER.matcher(message);
        return matcher.find() ? matcher.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    private String extractInvoiceNumber(String message) {
        Matcher matcher = INVOICE_IDENTIFIER.matcher(message);
        return matcher.find() ? matcher.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    private String extractCustomerCode(String message) {
        Matcher matcher = CUSTOMER_IDENTIFIER.matcher(message);
        return matcher.find() ? matcher.group().trim().toUpperCase(Locale.ROOT) : null;
    }

    private String extractPhoneNumber(String message) {
        Matcher matcher = PHONE_NUMBER.matcher(message);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private List<String> extractIdentifiers(String message) {
        java.util.ArrayList<String> identifiers = new java.util.ArrayList<>();
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(message);
        while (matcher.find()) {
            String identifier = matcher.group().trim().toUpperCase(Locale.ROOT);
            if (!identifiers.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        return List.copyOf(identifiers);
    }

    private String extractIdentifier(String message) {
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(message);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private String extractEntityQuery(String message, String entityType) {
        String lower = message.toLowerCase(Locale.ROOT);
        String[] triggers = {
                entityType + " details for ",
                entityType + " details of ",
                "details for " + entityType + " ",
                "details of " + entityType + " ",
                "details for ",
                "details of ",
                "sales history for " + entityType + " ",
                "purchase history for " + entityType + " ",
                "history for " + entityType + " ",
                entityType + " named ",
                entityType + " called ",
                "for " + entityType + " ",
                "to " + entityType + " ",
                "from " + entityType + " ",
                entityType + " "
        };
        for (String trigger : triggers) {
            int idx = lower.indexOf(trigger);
            if (idx >= 0) {
                String sub = message.substring(idx + trigger.length()).trim();
                return cleanTrailingPunctuation(sub);
            }
        }
        return "";
    }

    private String cleanTrailingPunctuation(String text) {
        return text.replaceAll("[?!.;,]+$", "")
                .replaceAll("\\s+(?:please|thanks?)$", "")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .trim();
    }

    private String extractSearchText(String message) {
        String identifier = extractIdentifier(message);
        if (!identifier.isEmpty()) {
            return identifier;
        }
        String cleaned = message.trim();
        boolean changed;
        do {
            String previous = cleaned;
            for (Pattern prefix : SEARCH_PREFIXES) {
                cleaned = prefix.matcher(cleaned).replaceFirst("");
            }
            changed = !cleaned.equals(previous);
        } while (changed);
        return cleanTrailingPunctuation(cleaned);
    }

    private boolean containsAny(String message, String... values) {
        for (String value : values) {
            if (message.contains(value)) return true;
        }
        return false;
    }
}
