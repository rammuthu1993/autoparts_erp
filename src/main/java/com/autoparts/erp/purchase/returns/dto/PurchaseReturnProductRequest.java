package com.autoparts.erp.purchase.returns.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReturnProductRequest(

    @NotNull(message = "Purchase product UUID is required")
    UUID purchaseProductUuid,

    @NotNull(message = "Return quantity is required")
    @DecimalMin(value = "0.001", message = "Return quantity must be greater than zero")
    BigDecimal quantity

) {}