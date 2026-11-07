package com.autoparts.erp.reports.controller;

import com.autoparts.erp.reports.dto.DashboardSummaryResponse;
import com.autoparts.erp.reports.service.ReportService;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public DashboardSummaryResponse getDashboardSummary(

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must be before or equal to toDate");
        }

        return reportService.getDashboardSummary(
                fromDate,
                toDate
        );
    }
}