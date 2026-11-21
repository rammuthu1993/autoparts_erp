package com.autoparts.erp.ai.model;

import java.util.Map;

/**
 * References a specific business entity identified in conversation context or request query.
 */
public record AiEntityReference(
        String entityType,
        String identifier,
        String name,
        String partNumber,
        Map<String, Object> attributes
) {
    public static AiEntityReference ofProduct(String productCode, String name, String partNumber) {
        return new AiEntityReference("PRODUCT", productCode, name, partNumber, Map.of());
    }

    public static AiEntityReference ofProduct(String productCode, String name, String partNumber, Map<String, Object> attributes) {
        return new AiEntityReference("PRODUCT", productCode, name, partNumber, attributes != null ? attributes : Map.of());
    }

    public static AiEntityReference ofProduct(String productCode) {
        return new AiEntityReference("PRODUCT", productCode, null, null, Map.of());
    }

    public static AiEntityReference ofCustomer(String customerIdentifier) {
        return new AiEntityReference("CUSTOMER", customerIdentifier, null, null, Map.of());
    }

    public static AiEntityReference ofSupplier(String supplierIdentifier) {
        return new AiEntityReference("SUPPLIER", supplierIdentifier, null, null, Map.of());
    }

    public static AiEntityReference ofInvoice(String invoiceNumber) {
        return new AiEntityReference("INVOICE", invoiceNumber, null, null, Map.of());
    }

    public static AiEntityReference ofPurchase(String purchaseNumber) {
        return new AiEntityReference("PURCHASE", purchaseNumber, null, null, Map.of());
    }
}
