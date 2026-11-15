package com.autoparts.erp.ai.service;

import java.util.Set;

public record ErpAiIntentResult(
        String intent,
        String searchText
) {

    private static final Set<String> SUPPORTED_INTENTS = Set.of(
            "PRODUCT_SEARCH",
            "PRODUCT_DETAILS",
            "PRODUCT_STOCK",
            "COUNT_PRODUCTS",
            "LOW_STOCK_PRODUCTS",
            "GENERAL",
            "UNSUPPORTED",
            "CLARIFICATION"
    );

    public ErpAiIntentResult {
        if (intent == null || !SUPPORTED_INTENTS.contains(intent)) {
            throw new IllegalArgumentException(
                    "Unsupported ERP AI intent: " + intent
            );
        }

        searchText = searchText == null ? "" : searchText.trim();
    }
}
