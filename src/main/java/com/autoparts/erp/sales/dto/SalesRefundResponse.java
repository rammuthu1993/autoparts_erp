package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesRefundResponse(
    UUID uuid,
    PaymentMethod paymentMethod,
    BigDecimal amount,
    String referenceNumber,
    OffsetDateTime refundDate,
    String notes,
    OffsetDateTime createdAt
) {}