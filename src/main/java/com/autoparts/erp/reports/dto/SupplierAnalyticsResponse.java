package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SupplierAnalyticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime generatedAt,
        String metricDefinition,
        List<SupplierMetric> suppliers) {

    public record SupplierMetric(
            Long supplierId,
            String supplierName,
            Long receivedPurchaseCount,
            BigDecimal purchaseTotal,
            BigDecimal paymentTotal,
            BigDecimal creditAppliedTotal,
            BigDecimal paymentCoveragePercentage,
            BigDecimal averageDaysToFirstPayment,
            BigDecimal averageLeadTimeDays,
            BigDecimal leadTimeVariabilityDays,
            BigDecimal returnedAmount,
            BigDecimal returnRatePercentage,
            BigDecimal purchaseConcentrationPercentage) {}
}
