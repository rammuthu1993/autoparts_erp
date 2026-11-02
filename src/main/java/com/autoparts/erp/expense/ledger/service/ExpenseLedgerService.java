package com.autoparts.erp.expense.ledger.service;

import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerResponse;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import java.time.LocalDate;
import java.util.UUID;

public interface ExpenseLedgerService {

    ExpenseLedgerResponse getLedger(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search,
        int page,
        int size
    );
}