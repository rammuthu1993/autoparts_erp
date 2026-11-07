package com.autoparts.erp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummaryResponse(

        LocalDate fromDate,

        LocalDate toDate,

        SalesSummary sales,

        PurchaseSummary purchases,

        ExpenseSummary expenses

) {

    public record SalesSummary(

            long invoiceCount,

            BigDecimal invoiceTotal,

            BigDecimal invoicePaidAmount,

            BigDecimal invoiceOutstandingAmount,

            BigDecimal collectionTotal,

            BigDecimal cashCollections,

            BigDecimal upiCollections,

            BigDecimal cardCollections,

            BigDecimal bankCollections,

            BigDecimal creditCollections

    ) {}

    public record PurchaseSummary(

            long purchaseCount,

            BigDecimal purchaseTotal,

            BigDecimal paymentTotal,

            BigDecimal cashPayments,

            BigDecimal upiPayments,

            BigDecimal cardPayments,

            BigDecimal bankPayments

    ) {}

    public record ExpenseSummary(

            long expenseCount,

            BigDecimal expenseTotal,

            BigDecimal cashExpenses,

            BigDecimal upiExpenses,

            BigDecimal cardExpenses,

            BigDecimal bankExpenses

    ) {}
}