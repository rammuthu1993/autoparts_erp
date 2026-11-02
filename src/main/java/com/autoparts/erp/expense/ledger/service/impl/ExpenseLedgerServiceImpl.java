package com.autoparts.erp.expense.ledger.service.impl;

import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerResponse;
import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerSummary;
import com.autoparts.erp.expense.ledger.repository.ExpenseLedgerRepository;
import com.autoparts.erp.expense.ledger.service.ExpenseLedgerService;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExpenseLedgerServiceImpl implements ExpenseLedgerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ExpenseLedgerRepository ledgerRepository;

    public ExpenseLedgerServiceImpl(
        ExpenseLedgerRepository ledgerRepository
    ) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public ExpenseLedgerResponse getLedger(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search,
        int page,
        int size
    ) {

        if (fromDate != null && toDate != null
            && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                "From date cannot be after to date"
            );
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                "Page must be zero or greater"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        long offset = (long) page * size;

        var entries = ledgerRepository.findEntries(
            fromDate,
            toDate,
            categoryUuid,
            paymentMethod,
            search,
            size,
            offset
        );

        ExpenseLedgerSummary summary = ledgerRepository.getSummary(
            fromDate,
            toDate,
            categoryUuid,
            paymentMethod,
            search
        );

        long totalElements = ledgerRepository.countEntries(
            fromDate,
            toDate,
            categoryUuid,
            paymentMethod,
            search
        );

        int totalPages = (int) Math.ceil(
            (double) totalElements / size
        );

        return new ExpenseLedgerResponse(
            summary,
            entries,
            page,
            size,
            totalElements,
            totalPages
        );
    }
}