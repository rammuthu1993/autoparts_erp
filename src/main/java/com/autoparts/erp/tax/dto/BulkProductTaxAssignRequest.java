package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BulkProductTaxAssignRequest(
    @NotEmpty(message = "Select at least one product")
    @Size(max = 200, message = "Maximum 200 products per bulk operation")
    List<UUID> productUuids,

    @NotNull(message = "GST rate is required")
    UUID taxRateUuid,

    @Size(max = 20)
    @Pattern(
        regexp = "^$|^[0-9A-Za-z]+$",
        message = "Invalid HSN code"
    )
    String hsnCode,

    @Size(max = 255)
    String hsnDescription,

    @NotNull(message = "Effective start date is required")
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    boolean supersedeExisting,

    @Size(max = 500)
    String changeReason
) {}
