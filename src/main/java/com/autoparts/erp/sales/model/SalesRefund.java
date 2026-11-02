package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesRefund(
    Long id,
    UUID uuid,
    Long salesReturnId,
    PaymentMethod paymentMethod,
    BigDecimal amount,
    String referenceNumber,
    OffsetDateTime refundDate,
    String notes,
    OffsetDateTime createdAt
) {}