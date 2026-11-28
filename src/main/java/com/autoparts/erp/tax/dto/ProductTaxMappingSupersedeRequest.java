package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record ProductTaxMappingSupersedeRequest(
    @NotNull(message = "Replacement GST rate is required")
    UUID taxRateUuid,

    @Size(max = 20)
    @Pattern(
        regexp = "^$|^[0-9A-Za-z]+$",
        message = "Invalid HSN code"
    )
    String hsnCode,

    @Size(max = 255)
    String hsnDescription,

    @NotNull(message = "Replacement effective start date is required")
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @NotBlank(message = "Reason is required when superseding a product tax mapping")
    @Size(max = 500)
    String changeReason
) {}
