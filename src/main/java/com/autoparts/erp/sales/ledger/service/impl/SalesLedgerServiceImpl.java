package com.autoparts.erp.sales.ledger.service.impl;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.sales.ledger.dto.SalesLedgerPageResponse;
import com.autoparts.erp.sales.ledger.dto.SalesLedgerSummaryResponse;
import com.autoparts.erp.sales.ledger.exception.SalesLedgerErrorCode;
import com.autoparts.erp.sales.ledger.repository.SalesLedgerRepository;
import com.autoparts.erp.sales.ledger.service.SalesLedgerService;

@Service
@Transactional(readOnly = true)
public class SalesLedgerServiceImpl implements SalesLedgerService {

    private static final Logger logger =
        LoggerFactory.getLogger(SalesLedgerServiceImpl.class);

    private final SalesLedgerRepository salesLedgerRepository;

    public SalesLedgerServiceImpl(
        SalesLedgerRepository salesLedgerRepository
    ) {
        this.salesLedgerRepository = salesLedgerRepository;
    }

    @Override
    public SalesLedgerPageResponse getSalesLedger(
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int page,
        int size
    ) {

        if (fromDate == null || toDate == null
                || fromDate.isAfter(toDate)) {

            throw new ApiException(
                SalesLedgerErrorCode.INVALID_DATE_RANGE
            );
        }

        if (page < 0 || size < 1 || size > 100) {

            throw new ApiException(
                SalesLedgerErrorCode.INVALID_PAGINATION
            );
        }

        int offset;

        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new ApiException(
                SalesLedgerErrorCode.INVALID_PAGINATION
            );
        }

        String normalizedSearch =
            search == null || search.isBlank()
                ? null
                : search.trim();

        logger.debug(
            "Fetching sales ledger from {} to {}, page {}, size {}",
            fromDate,
            toDate,
            page,
            size
        );

        SalesLedgerSummaryResponse summary =
            salesLedgerRepository.getSummary(
                fromDate,
                toDate,
                normalizedSearch
            );

        var entries = salesLedgerRepository.findEntries(
            fromDate,
            toDate,
            normalizedSearch,
            size,
            offset
        );

        long totalElements = salesLedgerRepository.countEntries(
            fromDate,
            toDate,
            normalizedSearch
        );

        int totalPages = totalElements == 0
            ? 0
            : (int) Math.ceil(
                (double) totalElements / size
            );

        boolean last = totalPages == 0 || page >= totalPages - 1;

        return new SalesLedgerPageResponse(
            summary,
            entries,
            page,
            size,
            totalElements,
            totalPages,
            last
        );
    }
}