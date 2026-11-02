package com.autoparts.erp.purchase.ledger.dto;

import com.autoparts.erp.purchase.model.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseLedgerEntry(
    UUID purchaseUuid,
    String purchaseNumber,
    String supplierName,
    String supplierInvoiceNo,
    LocalDate purchaseDate,
    PurchaseStatus status,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal grandTotal,
    OffsetDateTime receivedAt
) {
}