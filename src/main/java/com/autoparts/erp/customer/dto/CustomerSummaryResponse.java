package com.autoparts.erp.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerSummaryResponse(
        UUID uuid,
        String customerCode,
        String customerName,
        boolean active,
        long totalSalesCount,
        BigDecimal totalSalesAmount,
        BigDecimal totalPaymentsReceived,
        BigDecimal totalSalesReturns,
        BigDecimal outstandingReceivable,
        LocalDate lastSaleDate,
        LocalDate lastPaymentDate,
        BigDecimal creditLimit,
        BigDecimal availableCredit
) {}
