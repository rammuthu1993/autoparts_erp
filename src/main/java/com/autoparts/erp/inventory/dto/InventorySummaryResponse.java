package com.autoparts.erp.inventory.dto;

public record InventorySummaryResponse(
    long totalActiveProducts,
    long inStockProducts,
    long lowStockProducts,
    long outOfStockProducts
) {}
