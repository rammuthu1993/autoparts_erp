package com.autoparts.erp.reports.service;

import com.autoparts.erp.reports.dto.DashboardSummaryResponse;

import java.time.LocalDate;

public interface ReportService {

    DashboardSummaryResponse getDashboardSummary(
            LocalDate fromDate,
            LocalDate toDate
    );
}