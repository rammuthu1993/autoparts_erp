package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record InventoryAnalyticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime generatedAt,
        String metricDefinition,
        String valuationNotice,
        List<ProductMetric> products) {

    public record ProductMetric(
            Long productId,
            String productCode,
            String productName,
            String category,
            String brand,
            String unit,
            BigDecimal onHandQuantity,
            BigDecimal unitsSold,
            BigDecimal salesRevenue,
            BigDecimal averageMonthlyDemand,
            BigDecimal demandCoefficientOfVariation,
            String abcClass,
            String xyzClass,
            LocalDate lastMovementDate,
            Long daysSinceLastMovement,
            LocalDate lastSaleDate,
            Long daysSinceLastSale,
            String movementClass) {}
}
