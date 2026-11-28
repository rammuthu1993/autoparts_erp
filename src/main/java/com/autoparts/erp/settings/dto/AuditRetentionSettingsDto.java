package com.autoparts.erp.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record AuditRetentionSettingsDto(
        UUID uuid,

        @Min(value = 30, message = "Retention period must be at least 30 days")
        @Max(value = 3650, message = "Retention period cannot exceed 3650 days")
        int retentionDays,

        @NotBlank(message = "Archive frequency is required")
        @Pattern(regexp = "^(DAILY|WEEKLY|MONTHLY)$", message = "Archive frequency must be DAILY, WEEKLY, or MONTHLY")
        String archiveFrequency,

        @NotBlank(message = "Archive destination is required")
        @Size(max = 255, message = "Archive destination cannot exceed 255 characters")
        String archiveDestination,

        Instant lastArchiveAt,
        String lastArchiveStatus,
        String lastArchiveError,
        Instant updatedAt
) {}
