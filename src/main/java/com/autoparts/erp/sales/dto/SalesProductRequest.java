package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SalesProductRequest(

    @NotNull(message = "Product UUID is required")
    UUID productUuid,

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    BigDecimal quantity,

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.00")
    BigDecimal unitPrice,

    @DecimalMin(value = "0.00")
    BigDecimal discountAmount

) {}