
package com.autoparts.erp.dailyclosing.dto;

import java.math.BigDecimal;

public record DailyClosingSummary(

    // Sales
    Long salesCount,
    BigDecimal salesTotal,
    BigDecimal salesPaidTotal,
    BigDecimal salesBalanceTotal,

    // Sales payment collections
    BigDecimal salesCashTotal,
    BigDecimal salesUpiTotal,
    BigDecimal salesCardTotal,
    BigDecimal salesBankTotal,
    BigDecimal salesCreditTotal,

    // Sales returns
    Long salesReturnCount,
    BigDecimal salesReturnTotal,

    // Sales refunds
    BigDecimal salesRefundTotal,
    BigDecimal salesRefundCashTotal,
    BigDecimal salesRefundUpiTotal,
    BigDecimal salesRefundCardTotal,
    BigDecimal salesRefundBankTotal,
    BigDecimal salesRefundCreditTotal,

    // Purchases
    Long purchaseCount,
    BigDecimal purchaseTotal,

    // Purchase payments
    BigDecimal purchaseCashTotal,
    BigDecimal purchaseUpiTotal,
    BigDecimal purchaseCardTotal,
    BigDecimal purchaseBankTotal,

    // Expenses
    Long expenseCount,
    BigDecimal expenseTotal,

    BigDecimal expenseCashTotal,
    BigDecimal expenseUpiTotal,
    BigDecimal expenseCardTotal,
    BigDecimal expenseBankTotal,

    // Cash register
    Long registerCount,

    BigDecimal cashOpeningTotal,
    BigDecimal cashInTotal,
    BigDecimal cashOutTotal,
    BigDecimal cashExpectedTotal,
    BigDecimal cashActualTotal,
    BigDecimal cashDifferenceTotal
) {}
