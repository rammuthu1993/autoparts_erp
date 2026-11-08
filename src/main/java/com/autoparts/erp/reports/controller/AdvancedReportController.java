package com.autoparts.erp.reports.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

import com.autoparts.erp.reports.dto.DataQualityReportResponse;
import com.autoparts.erp.reports.dto.CustomerAnalyticsResponse;
import com.autoparts.erp.reports.dto.FinancialReconciliationResponse;
import com.autoparts.erp.reports.dto.InventoryAnalyticsResponse;
import com.autoparts.erp.reports.dto.IssueResolutionRequest;
import com.autoparts.erp.reports.dto.ReportTargetRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisResponse;
import com.autoparts.erp.reports.dto.TargetActualReportResponse;
import com.autoparts.erp.reports.dto.SupplierAnalyticsResponse;
import com.autoparts.erp.reports.service.AdvancedReportService;
import com.autoparts.erp.auth.security.annotation.AdminOnly;

@RestController
@Validated
@RequestMapping("/api/v1/reports")
public class AdvancedReportController {

    private final AdvancedReportService reportService;

    public AdvancedReportController(AdvancedReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/data-quality")
    public DataQualityReportResponse getDataQuality(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        return reportService.getDataQualityReport(fromDate, toDate);
    }

    @PatchMapping("/data-quality/issues/{issueKey}")
    @AdminOnly
    public void updateIssueResolution(
            @PathVariable String issueKey,
            @Valid @RequestBody IssueResolutionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        reportService.updateIssueResolution(
                issueKey, request.status(), request.notes(), jwt.getSubject());
    }

    @GetMapping("/reconciliation")
    public FinancialReconciliationResponse getReconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        return reportService.getReconciliationReport(fromDate, toDate);
    }

    @GetMapping("/inventory/advanced")
    public InventoryAnalyticsResponse getInventoryAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        return reportService.getInventoryAnalytics(fromDate, toDate);
    }

    @PostMapping("/scenarios")
    public ScenarioAnalysisResponse simulate(
            @Valid @RequestBody ScenarioAnalysisRequest request) {
        return reportService.simulate(request);
    }

    @GetMapping("/targets")
    public TargetActualReportResponse getTargets(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        return reportService.getTargetActualReport(fromDate, toDate);
    }

    @PostMapping("/targets")
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.UUID createTarget(
            @Valid @RequestBody ReportTargetRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return reportService.createTarget(request, jwt.getSubject());
    }

    @GetMapping("/customers/advanced")
    public CustomerAnalyticsResponse getCustomerAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        return reportService.getCustomerAnalytics(fromDate, toDate);
    }

    @GetMapping("/suppliers/advanced")
    public SupplierAnalyticsResponse getSupplierAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        return reportService.getSupplierAnalytics(fromDate, toDate);
    }
}
