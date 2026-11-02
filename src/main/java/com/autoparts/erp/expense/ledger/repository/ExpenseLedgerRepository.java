package com.autoparts.erp.expense.ledger.repository;

import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerEntry;
import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerSummary;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseLedgerRepository {

    List<ExpenseLedgerEntry> findEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search,
        int limit,
        long offset
    );

    ExpenseLedgerSummary getSummary(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search
    );

    long countEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search
    );
}