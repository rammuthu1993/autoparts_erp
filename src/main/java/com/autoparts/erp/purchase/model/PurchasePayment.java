package com.autoparts.erp.purchase.model;
import com.autoparts.erp.purchase.enums.PurchasePaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchasePayment(
    Long id,
    UUID uuid,
    Long purchaseId,
    PurchasePaymentMethod paymentMethod,
    BigDecimal amount,
    String referenceNumber,
    OffsetDateTime paymentDate,
    String notes,
    Long createdBy,
    OffsetDateTime createdAt
) {}