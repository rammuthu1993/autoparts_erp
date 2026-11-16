package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Set;

public record ErpAiIntentResult(
        String intent,
        String searchText,
        String startDate,
        String endDate,
        Integer limit,
        String clarificationMessage,
        List<String> productIdentifiers
) {
    private static final Set<String> SUPPORTED_INTENTS = Set.of(
            "PRODUCT_SEARCH", "PRODUCT_DETAILS", "PRODUCT_STOCK", "COUNT_PRODUCTS",
            "LOW_STOCK_PRODUCTS", "PRODUCT_MOVEMENT_HISTORY", "PRODUCT_MOVEMENT_SUMMARY",
            "PRODUCT_MOVEMENTS_BY_DATE_RANGE", "RECENT_INVENTORY_MOVEMENTS",
            "INVENTORY_ADJUSTMENTS", "PRODUCT_COMPARISON", "PURCHASE_SEARCH", "PURCHASE_DETAILS",
            "GENERAL", "UNSUPPORTED", "CLARIFICATION"
    );

    /** Backward-compatible constructor used by existing classifier/tests. */
    public ErpAiIntentResult(String intent, String searchText) {
        this(intent, searchText, "", "", null, "", List.of());
    }

    /** Backward-compatible constructor for the previous six-field record. */
    public ErpAiIntentResult(String intent, String searchText, String startDate,
                             String endDate, Integer limit, String clarificationMessage) {
        this(intent, searchText, startDate, endDate, limit, clarificationMessage, List.of());
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
        if (limit != null && (limit < 1 || limit > 50)) {
            throw new IllegalArgumentException("Inventory result limit must be between 1 and 50.");
        }
        if ("PRODUCT_COMPARISON".equals(intent)
                && (productIdentifiers.size() < 2 || productIdentifiers.size() > 5)) {
            throw new IllegalArgumentException("Product comparison requires between 2 and 5 unique product identifiers.");
        }
    }

    public static ErpAiIntentResult clarification(String message) {
        return new ErpAiIntentResult("CLARIFICATION", "", "", "", null, message, List.of());
    }
}
