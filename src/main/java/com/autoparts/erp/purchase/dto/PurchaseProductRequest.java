package com.autoparts.erp.purchase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PurchaseProductRequest(

    @NotNull
    @Positive
    Long productId,

    @NotNull
    @DecimalMin(value = "0.001")
    @Digits(integer = 9, fraction = 3)
    BigDecimal quantity,

    @NotNull
    @DecimalMin(value = "0.00")
    @Digits(integer = 10, fraction = 2)
    BigDecimal purchasePrice,

    @NotNull
    @DecimalMin(value = "0.00")
    @Digits(integer = 12, fraction = 2)
    BigDecimal discountAmount,

    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    @Digits(integer = 3, fraction = 2)
    BigDecimal taxPercent

) {}
