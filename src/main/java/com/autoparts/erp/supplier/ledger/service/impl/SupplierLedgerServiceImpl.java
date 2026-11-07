package com.autoparts.erp.supplier.ledger.service.impl;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerEntry;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerResponse;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerSummary;
import com.autoparts.erp.supplier.ledger.repository.SupplierLedgerRepository;
import com.autoparts.erp.supplier.ledger.service.SupplierLedgerService;
import com.autoparts.erp.supplier.model.SupplierErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SupplierLedgerServiceImpl implements SupplierLedgerService {

    private static final Logger log =
            LoggerFactory.getLogger(SupplierLedgerServiceImpl.class);

    private final SupplierLedgerRepository repository;

    public SupplierLedgerServiceImpl(
            SupplierLedgerRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public SupplierLedgerResponse getSupplierLedger(
            UUID supplierUuid,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {

        validateFilters(fromDate, toDate, page, size);

        SupplierLedgerRepository.SupplierInfo supplier =
                repository.findSupplierByUuid(supplierUuid)
                        .orElseThrow(() -> new ApiException(
                                SupplierErrorCode.SUPPLIER_LEDGER_NOT_FOUND
                        ));

        BigDecimal openingBalance = repository.getOpeningBalance(
                supplier.id(),
                fromDate
        );

        SupplierLedgerSummary summary = repository.getSummary(
                supplier,
                fromDate,
                toDate,
                openingBalance
        );

        long totalElements = repository.countEntries(
                supplier.id(),
                fromDate,
                toDate
        );

        int offset = Math.multiplyExact(page, size);

        List<SupplierLedgerEntry> entries = repository.findEntries(
                supplier.id(),
                fromDate,
                toDate,
                openingBalance,
                size,
                offset
        );

        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact(((totalElements - 1) / size) + 1);

        log.debug(
                "Supplier ledger retrieved. Supplier UUID: {}, " +
                "total entries: {}, page: {}",
                supplierUuid,
                totalElements,
                page
        );

        return new SupplierLedgerResponse(
                summary,
                entries,
                page,
                size,
                totalElements,
                totalPages
        );
    }

    private void validateFilters(
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {

        if (fromDate != null
                && toDate != null
                && fromDate.isAfter(toDate)) {

            throw new ApiException(
                    SupplierErrorCode.INVALID_SUPPLIER_LEDGER_FILTER
            );
        }

        if (page < 0
                || size < 1
                || size > 100
                || page > Integer.MAX_VALUE / size) {

            throw new ApiException(
                    SupplierErrorCode.INVALID_SUPPLIER_LEDGER_FILTER
            );
        }
    }
}