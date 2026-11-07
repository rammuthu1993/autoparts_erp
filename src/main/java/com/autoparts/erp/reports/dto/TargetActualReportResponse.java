package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TargetActualReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime generatedAt,
        String forecastDefinition,
        String unsupportedMetrics,
        List<TargetActual> targets) {

    public record TargetActual(
            UUID targetId,
            String metricKey,
            String dimensionName,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal target,
            BigDecimal actual,
            BigDecimal variance,
            BigDecimal achievementPercentage,
            BigDecimal remainingAmount,
            BigDecimal forecastedAchievement) {}
}
