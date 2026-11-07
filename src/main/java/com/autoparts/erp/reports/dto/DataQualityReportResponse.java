package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DataQualityReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal score,
        long recordsChecked,
        long affectedRecordCount,
        OffsetDateTime generatedAt,
        String metricDefinition,
        List<Issue> issues) {

    public record Issue(
            String issueKey,
            String category,
            String severity,
            String entityType,
            String recordId,
            String reference,
            String description,
            String resolutionStatus,
            String resolutionNotes) {}
}
