package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;

import com.autoparts.erp.sales.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SalesPaymentRequest(

    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod,

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01")
    BigDecimal amount,

    String referenceNumber,

    String notes

) {}