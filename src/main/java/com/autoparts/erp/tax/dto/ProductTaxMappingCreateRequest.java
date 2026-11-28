package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record ProductTaxMappingCreateRequest(

    @NotNull
    UUID taxRateUuid,

    @Size(max = 20)
    @Pattern(
        regexp = "^$|^[0-9A-Za-z]+$",
        message = "Invalid HSN code"
    )
    String hsnCode,

    @Size(max = 255)
    String hsnDescription,

    @NotNull
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @Size(max = 500)
    String changeReason

) {
    public ProductTaxMappingCreateRequest(
        UUID taxRateUuid,
        String hsnCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        this(taxRateUuid, hsnCode, null, effectiveFrom, effectiveTo, null);
    }
}
