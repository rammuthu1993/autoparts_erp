package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SalesReturnProductRequest(

    @NotNull(message = "Sales product UUID is required")
    UUID salesProductUuid,

    @NotNull(message = "Return quantity is required")
    @DecimalMin(
        value = "0.001",
        message = "Return quantity must be greater than zero"
    )
    BigDecimal quantity

) {}