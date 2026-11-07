package com.autoparts.erp.reports.service;

import java.time.LocalDate;

import com.autoparts.erp.reports.dto.DataQualityReportResponse;
import com.autoparts.erp.reports.dto.CustomerAnalyticsResponse;
import com.autoparts.erp.reports.dto.FinancialReconciliationResponse;
import com.autoparts.erp.reports.dto.InventoryAnalyticsResponse;
import com.autoparts.erp.reports.dto.ReportTargetRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisResponse;
import com.autoparts.erp.reports.dto.TargetActualReportResponse;
import com.autoparts.erp.reports.dto.SupplierAnalyticsResponse;

public interface AdvancedReportService {

    DataQualityReportResponse getDataQualityReport(
            LocalDate fromDate,
            LocalDate toDate);

    void updateIssueResolution(
            String issueKey,
            String status,
            String notes,
            String username);

    FinancialReconciliationResponse getReconciliationReport(
            LocalDate fromDate,
            LocalDate toDate);

    InventoryAnalyticsResponse getInventoryAnalytics(
            LocalDate fromDate,
            LocalDate toDate);

    ScenarioAnalysisResponse simulate(ScenarioAnalysisRequest request);

    TargetActualReportResponse getTargetActualReport(
            LocalDate fromDate,
            LocalDate toDate);

    java.util.UUID createTarget(ReportTargetRequest request, String username);

    CustomerAnalyticsResponse getCustomerAnalytics(
            LocalDate fromDate,
            LocalDate toDate);

    SupplierAnalyticsResponse getSupplierAnalytics(
            LocalDate fromDate,
            LocalDate toDate);
}
