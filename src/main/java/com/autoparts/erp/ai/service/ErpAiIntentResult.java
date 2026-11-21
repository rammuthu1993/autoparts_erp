package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Set;

import com.autoparts.erp.ai.enums.ClassificationSource;

public record ErpAiIntentResult(
        String intent,
        String searchText,
        String startDate,
        String endDate,
        Integer limit,
        String clarificationMessage,
        List<String> productIdentifiers,
        double confidence,
        ClassificationSource source,
        boolean isMultiIntent,
        String secondaryIntent,
        String pendingSlotName
) {
    public static final Set<String> SUPPORTED_INTENTS = Set.of(
            // Products & Inventory (Stage 2B)
            "PRODUCT_SEARCH", "PRODUCT_DETAILS", "PRODUCT_STOCK",
            "PRODUCT_MINIMUM_STOCK", "PRODUCT_AVAILABLE_QUANTITY",
            "PRODUCT_PART_NUMBER", "PRODUCT_LOW_STOCK_STATUS", "COUNT_PRODUCTS",
            "LOW_STOCK_PRODUCTS", "PRODUCT_MOVEMENT_HISTORY", "PRODUCT_MOVEMENT_SUMMARY",
            "PRODUCT_MOVEMENTS_BY_DATE_RANGE", "RECENT_INVENTORY_MOVEMENTS",
            "INVENTORY_ADJUSTMENTS", "PRODUCT_COMPARISON",

            // Purchases (Stage 2C)
            "PURCHASE_SEARCH", "PURCHASE_DETAILS", "PURCHASE_DATE_RANGE",
            "PURCHASES_BY_SUPPLIER", "PURCHASE_SUMMARY", "PURCHASE_STATUS",

            // Sales (Stage 2C)
            "SALES_SEARCH", "SALE_DETAILS", "SALES_DATE_RANGE",
            "SALES_BY_CUSTOMER", "SALES_SUMMARY", "SALE_STATUS",

            // Customers (Stage 2C)
            "CUSTOMER_SEARCH", "CUSTOMER_DETAILS", "CUSTOMER_SALES_HISTORY",
            "CUSTOMER_OUTSTANDING",

            // Suppliers (Stage 2C)
            "SUPPLIER_SEARCH", "SUPPLIER_DETAILS", "SUPPLIER_PURCHASE_HISTORY",
            "SUPPLIER_SUMMARY",

            // Returns (Stage 2D)
            "SALES_RETURN_SEARCH", "SALES_RETURN_DETAILS", "PURCHASE_RETURN_SEARCH",
            "PURCHASE_RETURN_DETAILS", "RETURN_SUMMARY",

            // Financials (Stage 2D)
            "CASH_REGISTER_STATUS", "OPEN_CASH_REGISTERS", "EXPENSE_SUMMARY", "EXPENSE_BY_CATEGORY",
            "EXPENSES_BY_CATEGORY", "DAILY_CLOSING", "CASH_DISCREPANCIES",

            // Reports (Stage 2D)
            "KPI_SUMMARY", "INVENTORY_ABC_XYZ", "INVENTORY_ABC_ANALYSIS", "CUSTOMER_RFM",
            "CUSTOMER_RFM_ANALYSIS", "RECONCILIATION_DISCREPANCIES", "COGS_PROFIT_EXPLANATION",

            // Audit Logs (Stage 2D)
            "AUDIT_LOG_SEARCH", "AI_TOOL_RUNS_LOG",

            // Knowledge RAG (Stage 3B)
            "KNOWLEDGE",

            // Core
            "GENERAL", "UNSUPPORTED", "CLARIFICATION"
    );

    /** Backward-compatible constructor for two-field record. */
    public ErpAiIntentResult(String intent, String searchText) {
        this(intent, searchText, "", "", null, "", List.of(), 1.0,
                ClassificationSource.DETERMINISTIC, false, "", "");
    }

    /** Backward-compatible constructor for six-field record. */
    public ErpAiIntentResult(String intent, String searchText, String startDate,
                             String endDate, Integer limit, String clarificationMessage) {
        this(intent, searchText, startDate, endDate, limit, clarificationMessage, List.of(), 1.0,
                ClassificationSource.DETERMINISTIC, false, "", "");
    }

    /** Backward-compatible constructor for seven-field record. */
    public ErpAiIntentResult(String intent, String searchText, String startDate,
                             String endDate, Integer limit, String clarificationMessage,
                             List<String> productIdentifiers) {
        this(intent, searchText, startDate, endDate, limit, clarificationMessage, productIdentifiers,
                1.0, ClassificationSource.DETERMINISTIC, false, "", "");
    }

    public ErpAiIntentResult {
        if (intent == null || !SUPPORTED_INTENTS.contains(intent)) {
            throw new IllegalArgumentException("Unsupported ERP AI intent: " + intent);
        }
        searchText = searchText == null ? "" : searchText.trim();
        startDate = startDate == null ? "" : startDate.trim();
        endDate = endDate == null ? "" : endDate.trim();
        clarificationMessage = clarificationMessage == null ? "" : clarificationMessage.trim();
        productIdentifiers = productIdentifiers == null ? List.of() : productIdentifiers.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        source = source == null ? ClassificationSource.DETERMINISTIC : source;
        secondaryIntent = secondaryIntent == null ? "" : secondaryIntent.trim();
        pendingSlotName = pendingSlotName == null ? "" : pendingSlotName.trim();
        if (confidence < 0.0 || confidence > 1.0) {
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }

        if (limit != null && (limit < 1 || limit > 50)) {
            throw new IllegalArgumentException("Inventory result limit must be between 1 and 50.");
        }
        if ("PRODUCT_COMPARISON".equals(intent)
                && (productIdentifiers.size() < 2 || productIdentifiers.size() > 5)) {
            throw new IllegalArgumentException("Product comparison requires between 2 and 5 unique product identifiers.");
        }
    }

    public static boolean isSupported(String intent) {
        return intent != null && SUPPORTED_INTENTS.contains(intent);
    }

    public static Set<String> supportedIntents() {
        return SUPPORTED_INTENTS;
    }

    public static ErpAiIntentResult clarification(String message) {
        return new ErpAiIntentResult("CLARIFICATION", "", "", "", null, message, List.of(),
                1.0, ClassificationSource.DETERMINISTIC, false, "", "");
    }

    public static ErpAiIntentResult clarification(String message, String pendingSlotName) {
        return new ErpAiIntentResult("CLARIFICATION", "", "", "", null, message, List.of(),
                1.0, ClassificationSource.DETERMINISTIC, false, "", pendingSlotName);
    }
}
