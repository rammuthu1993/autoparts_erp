package com.autoparts.erp.purchase.ledger.service.impl;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.purchase.exception.PurchaseErrorCode;
import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerResponse;
import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerSummary;
import com.autoparts.erp.purchase.ledger.repository.PurchaseLedgerRepository;
import com.autoparts.erp.purchase.ledger.service.PurchaseLedgerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PurchaseLedgerServiceImpl implements PurchaseLedgerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseLedgerRepository ledgerRepository;

    public PurchaseLedgerServiceImpl(
            PurchaseLedgerRepository ledgerRepository
    ) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public PurchaseLedgerResponse getLedger(
            LocalDate fromDate,
            LocalDate toDate,
            UUID supplierUuid,
            String search,
            int page,
            int size
    ) {

        // Validate date range
        if (fromDate != null
                && toDate != null
                && fromDate.isAfter(toDate)) {

            throw new ApiException(
                    PurchaseErrorCode.INVALID_PURCHASE_LEDGER_FILTER
            );
        }

        // Validate page number
        if (page < 0) {

            throw new ApiException(
                    PurchaseErrorCode.INVALID_PURCHASE_LEDGER_FILTER
            );
        }

        // Validate page size
        if (size < 1 || size > MAX_PAGE_SIZE) {

            throw new ApiException(
                    PurchaseErrorCode.INVALID_PURCHASE_LEDGER_FILTER
            );
        }

        long offset = (long) page * size;

        var entries = ledgerRepository.findEntries(
                fromDate,
                toDate,
                supplierUuid,
                search,
                size,
                offset
        );

        PurchaseLedgerSummary summary = ledgerRepository.getSummary(
                fromDate,
                toDate,
                supplierUuid,
                search
        );

        long totalElements = ledgerRepository.countEntries(
                fromDate,
                toDate,
                supplierUuid,
                search
        );

        int totalPages = (int) Math.ceil(
                (double) totalElements / size
        );

        return new PurchaseLedgerResponse(
                summary,
                entries,
                page,
                size,
                totalElements,
                totalPages
        );
    }
}