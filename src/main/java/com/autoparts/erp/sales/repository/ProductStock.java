package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;

public record ProductStock(
    Long productId,
    BigDecimal quantity,
    String productCode,
    String productName,
    String unit
) {}