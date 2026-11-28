package com.autoparts.erp.supplier.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierSummaryResponse(
        UUID uuid,
        String supplierCode,
        String supplierName,
        boolean active,
        long totalPurchasesCount,
        BigDecimal totalPurchasesAmount,
        BigDecimal totalPaymentsMade,
        BigDecimal totalPurchaseReturns,
        BigDecimal outstandingPayable,
        LocalDate lastPurchaseDate,
        LocalDate lastPaymentDate,
        BigDecimal creditLimit,
        Integer paymentTermsDays
) {}
