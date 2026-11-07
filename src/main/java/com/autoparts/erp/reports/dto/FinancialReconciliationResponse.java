package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FinancialReconciliationResponse(
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime generatedAt,
        String metricDefinition,
        List<Discrepancy> discrepancies) {

    public record Discrepancy(
            String category,
            String severity,
            String entityType,
            String recordId,
            String reference,
            BigDecimal sourceAmount,
            BigDecimal comparisonAmount,
            BigDecimal difference,
            String description) {}
}
