package com.autoparts.erp.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialYearSettingsDto(
        UUID uuid,

        @NotBlank(message = "Financial year label is required")
        @Size(max = 30, message = "Financial year label cannot exceed 30 characters")
        String fyLabel,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        LocalDate periodLockDate,
        boolean preventLockedPeriodEdits,

        @Size(max = 500, message = "Reopen reason cannot exceed 500 characters")
        String reopenReason,

        String lastReopenReason,
        boolean current,
        Instant updatedAt
) {}
