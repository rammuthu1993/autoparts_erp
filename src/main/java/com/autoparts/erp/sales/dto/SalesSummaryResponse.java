package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;

public record SalesSummaryResponse(
    long totalInvoices,
    long draftCount,
    long completedCount,
    long cancelledCount,
    long unpaidCount,
    long partiallyPaidCount,
    long paidCount,
    BigDecimal totalSalesAmount,
    BigDecimal totalCollectedAmount,
    BigDecimal outstandingReceivables,
    BigDecimal totalReturnedAmount,
    BigDecimal totalRefundedAmount
) {}
