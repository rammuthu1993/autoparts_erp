package com.autoparts.erp.reports.serviceimpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.autoparts.erp.reports.dto.DataQualityReportResponse.Issue;
import com.autoparts.erp.reports.dto.ReportTargetRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisRequest;
import com.autoparts.erp.reports.repository.AdvancedReportRepository;
import com.autoparts.erp.reports.repository.AdvancedReportRepository.ScenarioBaselineData;

class AdvancedReportServiceImplTests {

    private AdvancedReportRepository repository;
    private AdvancedReportServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AdvancedReportRepository.class);
        service = new AdvancedReportServiceImpl(
                repository, "Asia/Kolkata");
    }

    @Test
    void scenarioIsHypotheticalAndCompoundsPriceAndVolumeChanges() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(repository.getScenarioBaseline(from, to)).thenReturn(
                new ScenarioBaselineData(amount("1000"), amount("100")));

        var result = service.simulate(new ScenarioAnalysisRequest(
                from, to, amount("5"), amount("15"), amount("-8")));

        assertEquals("HYPOTHETICAL", result.valueType());
        assertEquals(amount("1207.50"), result.revenueScenario());
        assertEquals(amount("207.50"), result.revenueDifference());
        assertEquals(amount("20.75"), result.revenuePercentageChange());
        assertEquals(amount("92.00"), result.expenseScenario());
        assertEquals(amount("-8.00"), result.expenseDifference());
        assertEquals(amount("-8.00"), result.expensePercentageChange());
        verify(repository, never()).createTarget(Mockito.any(), Mockito.anyString());
    }

    @Test
    void dataQualityScoreUsesUniqueAffectedRecords() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        Issue productIssue = issue("PRODUCT_INFORMATION:PRODUCT:1", "PRODUCT", "1");
        Issue secondProductIssue = issue(
                "INVENTORY_INCONSISTENCY:PRODUCT:1", "PRODUCT", "1");
        when(repository.findDataQualityIssues(
                eq(from), eq(to), any(), any(), eq("Asia/Kolkata")))
                .thenReturn(List.of(productIssue, secondProductIssue));
        when(repository.countRecordsChecked(eq(from), eq(to), any(), any()))
                .thenReturn(10L);

        var result = service.getDataQualityReport(from, to);

        assertEquals(amount("90.00"), result.score());
        assertEquals(1, result.affectedRecordCount());
        assertEquals(2, result.issues().size());
    }

    @Test
    void targetConfigurationRejectsMismatchedDimensions() {
        ReportTargetRequest request = new ReportTargetRequest(
                "PRODUCT_SALES",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                2L,
                amount("1000"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createTarget(request, "admin"));
        verify(repository, never()).createTarget(Mockito.any(), Mockito.anyString());
    }

    @Test
    void dateRangeMustBeOrdered() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getReconciliationReport(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1)));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static Issue issue(String key, String entityType, String recordId) {
        return new Issue(
                key, "DATA_QUALITY", "MEDIUM", entityType, recordId,
                recordId, "Issue", "OPEN", null);
    }
}
