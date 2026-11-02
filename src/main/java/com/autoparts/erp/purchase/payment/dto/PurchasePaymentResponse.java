package com.autoparts.erp.purchase.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.purchase.enums.PurchasePaymentMethod;

public record PurchasePaymentResponse(

    UUID uuid,
    PurchasePaymentMethod paymentMethod,
    BigDecimal amount,
    String referenceNumber,
    OffsetDateTime paymentDate,
    String notes,
    OffsetDateTime createdAt

) {}