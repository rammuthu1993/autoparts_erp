package com.autoparts.erp.tax.dto;

public record TaxSummaryResponse(
    long currentRatesCount,
    long upcomingRatesCount,
    long expiredRatesCount,
    long inactiveRatesCount,
    long productsWithCurrentMapping,
    long productsMissingMapping,
    long productsWithUpcomingMapping
) {}
