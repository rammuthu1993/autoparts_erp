
package com.autoparts.erp.purchase.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Purchase(
    Long id,
    UUID uuid,
    String purchaseNumber,
    Long supplierId,
    String supplierName,
    String supplierInvoiceNo,
    LocalDate purchaseDate,
    PurchaseStatus status,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal grandTotal,
    String notes,
    List<PurchaseProduct> products,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime receivedAt
) {}
