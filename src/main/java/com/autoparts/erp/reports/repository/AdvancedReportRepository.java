package com.autoparts.erp.reports.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.reports.dto.DataQualityReportResponse.Issue;
import com.autoparts.erp.reports.dto.CustomerAnalyticsResponse.CustomerMetric;
import com.autoparts.erp.reports.dto.FinancialReconciliationResponse.Discrepancy;
import com.autoparts.erp.reports.dto.InventoryAnalyticsResponse.ProductMetric;
import com.autoparts.erp.reports.dto.ReportTargetRequest;
import com.autoparts.erp.reports.dto.SupplierAnalyticsResponse.SupplierMetric;

public interface AdvancedReportRepository {

    List<Issue> findDataQualityIssues(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone);

    long countRecordsChecked(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp);

    void updateIssueResolution(
            String issueKey,
            String status,
            String notes,
            String username);

    List<Discrepancy> findReconciliationDiscrepancies(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone);

    List<ProductMetric> findInventoryMetrics(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone);

    UUID createTarget(ReportTargetRequest request, String username);

    List<TargetActualData> findTargetActuals(
            LocalDate fromDate,
            LocalDate toDate);

    List<CustomerMetric> findCustomerMetrics(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp);

    List<SupplierMetric> findSupplierMetrics(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime toTimestamp,
            String businessTimeZone);

    ScenarioBaselineData getScenarioBaseline(
            LocalDate fromDate,
            LocalDate toDate);

    record TargetActualData(
            UUID targetId,
            String metricKey,
            String dimensionName,
            LocalDate periodStart,
            LocalDate periodEnd,
            java.math.BigDecimal target,
            java.math.BigDecimal actual) {}

    record ScenarioBaselineData(
            java.math.BigDecimal revenue,
            java.math.BigDecimal expenses) {}
}
