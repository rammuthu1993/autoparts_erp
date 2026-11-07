package com.autoparts.erp.reports.serviceimpl;

import com.autoparts.erp.reports.dto.DashboardSummaryResponse;
import com.autoparts.erp.reports.repository.ReportRepository;
import com.autoparts.erp.reports.service.ReportService;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    private final ZoneId businessZone;

    public ReportServiceImpl(
            ReportRepository reportRepository,
            @Value("${app.business.time-zone:Asia/Kolkata}")
            String businessTimeZone) {

        this.reportRepository = reportRepository;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "fromDate and toDate are required");
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must be before or equal to toDate");
        }

        var fromTimestamp = fromDate
                .atStartOfDay(businessZone)
                .toOffsetDateTime();

        var toTimestamp = toDate
                .plusDays(1)
                .atStartOfDay(businessZone)
                .toOffsetDateTime();

        var sales = reportRepository.getSalesSummary(
                fromDate,
                toDate,
                fromTimestamp,
                toTimestamp
        );

        var purchases = reportRepository.getPurchaseSummary(
                fromDate,
                toDate,
                fromTimestamp,
                toTimestamp
        );

        var expenses = reportRepository.getExpenseSummary(
                fromDate,
                toDate
        );

        return new DashboardSummaryResponse(
                fromDate,
                toDate,
                sales,
                purchases,
                expenses
        );
    }
}