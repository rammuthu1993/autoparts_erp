package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesPaymentResponse(
    UUID uuid,
    PaymentMethod paymentMethod,
    BigDecimal amount,
    String referenceNumber,
    OffsetDateTime paymentDate,
    String notes
) {}