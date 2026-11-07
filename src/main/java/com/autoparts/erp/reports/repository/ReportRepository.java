package com.autoparts.erp.reports.repository;

import com.autoparts.erp.reports.dto.DashboardSummaryResponse.ExpenseSummary;
import com.autoparts.erp.reports.dto.DashboardSummaryResponse.PurchaseSummary;
import com.autoparts.erp.reports.dto.DashboardSummaryResponse.SalesSummary;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface ReportRepository {

    SalesSummary getSalesSummary(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp
    );

    PurchaseSummary getPurchaseSummary(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp
    );

    ExpenseSummary getExpenseSummary(
            LocalDate fromDate,
            LocalDate toDate
    );
}