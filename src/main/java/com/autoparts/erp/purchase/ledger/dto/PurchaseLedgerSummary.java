package com.autoparts.erp.purchase.ledger.dto;

import java.math.BigDecimal;

public record PurchaseLedgerSummary(
    long totalPurchases,
    BigDecimal totalSubtotal,
    BigDecimal totalDiscount,
    BigDecimal totalTax,
    BigDecimal totalPurchaseAmount
) {
}