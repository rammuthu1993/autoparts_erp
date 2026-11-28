package com.autoparts.erp.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record InvoiceSettingsDto(
        UUID uuid,

        @NotBlank(message = "Invoice prefix is required")
        @Size(max = 20, message = "Invoice prefix cannot exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invoice prefix may only contain letters, numbers, hyphens, and underscores")
        String invoicePrefix,

        @Min(value = 4, message = "Number padding must be at least 4")
        @Max(value = 10, message = "Number padding cannot exceed 10")
        int numberPadding,

        @NotBlank(message = "Financial year reset policy is required")
        @Pattern(regexp = "^(NONE|ANNUAL_FY)$", message = "Reset policy must be NONE or ANNUAL_FY")
        String fyResetPolicy,

        @NotBlank(message = "Print page size is required")
        @Pattern(regexp = "^(A4|A5|THERMAL_80MM)$", message = "Print page size must be A4, A5, or THERMAL_80MM")
        String printPageSize,

        @Min(value = 1, message = "Copy count must be at least 1")
        @Max(value = 5, message = "Copy count cannot exceed 5")
        int copyCount,

        boolean showGstBreakdown,
        boolean showHsnSac,

        @Size(max = 1000, message = "Terms and conditions cannot exceed 1000 characters")
        String termsAndConditions,

        @Size(max = 500, message = "Footer text cannot exceed 500 characters")
        String footerText,

        Instant updatedAt
) {}
