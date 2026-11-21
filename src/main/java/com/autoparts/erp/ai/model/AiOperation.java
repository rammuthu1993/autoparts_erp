package com.autoparts.erp.ai.model;

/**
 * Concrete operations executable by the Auto Parts ERP AI orchestration pipeline.
 */
public enum AiOperation {
    // Products
    PRODUCT_SEARCH,
    PRODUCT_COUNT,
    PRODUCT_DETAILS,
    PRODUCT_STOCK,
    PRODUCT_AVAILABLE_QUANTITY,
    PRODUCT_MINIMUM_STOCK,
    PRODUCT_LOW_STOCK,
    PRODUCT_COMPARISON,

    // Inventory Movements & Adjustments
    INVENTORY_LOOKUP,
    PRODUCT_MOVEMENT_HISTORY,
    PRODUCT_MOVEMENT_SUMMARY,
    PRODUCT_MOVEMENTS_BY_DATE_RANGE,
    RECENT_INVENTORY_MOVEMENTS,
    INVENTORY_ADJUSTMENTS,

    // Customers
    CUSTOMER_LOOKUP,
    CUSTOMER_SEARCH,
    CUSTOMER_DETAILS,
    CUSTOMER_SALES_HISTORY,
    CUSTOMER_OUTSTANDING,

    // Suppliers
    SUPPLIER_LOOKUP,
    SUPPLIER_SEARCH,
    SUPPLIER_DETAILS,
    SUPPLIER_PURCHASE_HISTORY,
    SUPPLIER_SUMMARY,

    // Sales
    SALES_LOOKUP,
    SALES_SEARCH,
    SALE_DETAILS,
    SALES_DATE_RANGE,
    SALES_BY_CUSTOMER,
    SALES_SUMMARY,
    SALE_STATUS,

    // Purchases
    PURCHASE_LOOKUP,
    PURCHASE_SEARCH,
    PURCHASE_DETAILS,
    PURCHASE_DATE_RANGE,
    PURCHASES_BY_SUPPLIER,
    PURCHASE_SUMMARY,
    PURCHASE_STATUS,

    // Returns
    RETURN_SUMMARY,
    SALES_RETURN_SEARCH,
    SALES_RETURN_DETAILS,
    PURCHASE_RETURN_SEARCH,
    PURCHASE_RETURN_DETAILS,

    // Financial
    FINANCIAL_LOOKUP,
    CASH_REGISTER_STATUS,
    OPEN_CASH_REGISTERS,
    EXPENSE_SUMMARY,
    EXPENSE_BY_CATEGORY,
    EXPENSES_BY_CATEGORY,
    DAILY_CLOSING,
    CASH_DISCREPANCIES,

    // Reports & Analytics
    REPORT_LOOKUP,
    KPI_SUMMARY,
    INVENTORY_ABC_XYZ,
    INVENTORY_ABC_ANALYSIS,
    CUSTOMER_RFM,
    CUSTOMER_RFM_ANALYSIS,
    RECONCILIATION_DISCREPANCIES,
    COGS_PROFIT_EXPLANATION,

    // Audit
    AUDIT_LOOKUP,
    AUDIT_LOG_SEARCH,
    AI_TOOL_RUNS_LOG,

    // Knowledge / RAG
    KNOWLEDGE_QUERY,

    // Control & Conversational
    CLARIFICATION,
    UNSUPPORTED,
    GENERAL;

    public static AiOperation fromIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return CLARIFICATION;
        }
        try {
            return AiOperation.valueOf(intent.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return switch (intent.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "COUNT_PRODUCTS" -> PRODUCT_COUNT;
                case "LOW_STOCK_PRODUCTS" -> PRODUCT_LOW_STOCK;
                case "INVENTORY_ABC_XYZ" -> INVENTORY_ABC_ANALYSIS;
                case "CUSTOMER_RFM" -> CUSTOMER_RFM_ANALYSIS;
                case "EXPENSE_BY_CATEGORY" -> EXPENSES_BY_CATEGORY;
                default -> GENERAL;
            };
        }
    }
}
