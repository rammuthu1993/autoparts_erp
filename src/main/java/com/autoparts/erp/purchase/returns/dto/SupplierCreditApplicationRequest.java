package com.autoparts.erp.purchase.returns.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierCreditApplicationRequest(

    @NotNull(message = "Purchase UUID is required")
    UUID purchaseUuid,

    @NotNull(message = "Application amount is required")
    @DecimalMin(value = "0.01", message = "Application amount must be greater than zero")
    BigDecimal amount

) {}