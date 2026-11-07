package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ScenarioAnalysisResponse(
        String valueType,
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime generatedAt,
        BigDecimal revenueBaseline,
        BigDecimal revenueScenario,
        BigDecimal revenueDifference,
        BigDecimal revenuePercentageChange,
        BigDecimal expenseBaseline,
        BigDecimal expenseScenario,
        BigDecimal expenseDifference,
        BigDecimal expensePercentageChange,
        BigDecimal grossProfitBaseline,
        BigDecimal grossProfitScenario,
        String grossProfitUnavailableReason,
        String metricDefinition) {}
