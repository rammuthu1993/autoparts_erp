package com.autoparts.erp.reports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IssueResolutionRequest(
        @NotBlank
        @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED")
        String status,

        @Size(max = 1000)
        String notes) {}
