package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.autoparts.erp.sales.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalesCancellationRefundAllocation(
    @NotNull(message = "Refund payment method is required")
    PaymentMethod paymentMethod,
    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    BigDecimal amount,
    @Size(max = 100, message = "Refund reference must not exceed 100 characters")
    String referenceNumber,
    UUID originalPaymentUuid
) {}
