package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.CancellationRefundStatus;
import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesCancellationRefund(
    Long id,
    UUID uuid,
    Long cancellationId,
    Long saleId,
    Long originalPaymentId,
    PaymentMethod paymentMethod,
    BigDecimal amount,
    CancellationRefundStatus status,
    String referenceNumber,
    OffsetDateTime refundDate,
    OffsetDateTime confirmedAt,
    Long confirmedBy,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
