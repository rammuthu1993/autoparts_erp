package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxRateSupersedeRequest(
    @NotBlank
    @Size(max = 50)
    String taxName,

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    BigDecimal gstRate,

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    BigDecimal cgstRate,

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    BigDecimal sgstRate,

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    BigDecimal igstRate,

    @NotNull(message = "Replacement effective start date is required")
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @NotBlank(message = "Reason is required when superseding a tax rate")
    @Size(max = 500)
    String changeReason
) {}
