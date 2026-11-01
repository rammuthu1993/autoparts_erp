package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductStock(
    Long productId,
    UUID productUuid,
    BigDecimal quantity,
    String productCode,
    String productName,
    String unit
) {}