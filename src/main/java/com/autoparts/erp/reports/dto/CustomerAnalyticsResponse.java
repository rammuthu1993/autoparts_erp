package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CustomerAnalyticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime generatedAt,
        String metricDefinition,
        List<CustomerMetric> customers) {

    public record CustomerMetric(
            Long customerId,
            String customerCode,
            String customerName,
            LocalDate cohortMonth,
            Long recencyDays,
            Long purchaseFrequency,
            BigDecimal monetaryValue,
            BigDecimal averageOrderValue,
            Long activeMonths,
            Integer recencyScore,
            Integer frequencyScore,
            Integer monetaryScore,
            String segment) {}
}
