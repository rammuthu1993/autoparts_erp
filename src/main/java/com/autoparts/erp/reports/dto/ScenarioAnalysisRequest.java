package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ScenarioAnalysisRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,

        @DecimalMin("-100.00")
        @DecimalMax("1000.00")
        BigDecimal sellingPriceChangePercent,

        @DecimalMin("-100.00")
        @DecimalMax("1000.00")
        BigDecimal salesVolumeChangePercent,

        @DecimalMin("-100.00")
        @DecimalMax("1000.00")
        BigDecimal expenseChangePercent) {}
