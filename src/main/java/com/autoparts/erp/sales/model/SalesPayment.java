package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesPayment(
    Long id,
    UUID uuid,
    Long saleId,
    PaymentMethod paymentMethod,
    BigDecimal amount,
    String referenceNumber,
    OffsetDateTime paymentDate,
    String notes,
    OffsetDateTime createdAt
) {}