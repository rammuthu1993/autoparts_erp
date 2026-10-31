
package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.NotBlank;
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
        regexp = "^[0-9A-Za-z]+$",
        message = "Invalid HSN code"
    )
    String hsnCode,

    @NotNull
    LocalDate effectiveFrom,

    LocalDate effectiveTo

) {}
