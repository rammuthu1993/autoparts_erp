package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProductTaxMappingCloseRequest(
    @NotNull(message = "Effective end date is required")
    LocalDate effectiveTo,

    @NotBlank(message = "Reason is required when closing a product tax mapping")
    @Size(max = 500)
    String changeReason
) {}
