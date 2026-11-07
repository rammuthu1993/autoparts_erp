package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReportTargetRequest(
        @NotNull
        @Pattern(regexp = "MONTHLY_SALES|PRODUCT_SALES|CATEGORY_REVENUE|EXPENSE_BUDGET")
        String metricKey,

        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,

        Long productId,
        Long categoryId,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal targetValue) {}
