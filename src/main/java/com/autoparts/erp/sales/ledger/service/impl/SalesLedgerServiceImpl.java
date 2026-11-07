package com.autoparts.erp.sales.ledger.service.impl;

import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.customer.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    public SalesLedgerServiceImpl(
        SalesLedgerRepository salesLedgerRepository,
        CustomerRepository customerRepository
    ) {
        this.salesLedgerRepository = salesLedgerRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public SalesLedgerPageResponse getSalesLedger(
        UUID customerUuid,
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int page,
        int size
    ) {
        if (customerUuid == null) {
            throw new ApiException(SalesLedgerErrorCode.CUSTOMER_NOT_FOUND);
        }

        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ApiException(SalesLedgerErrorCode.INVALID_DATE_RANGE);
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(SalesLedgerErrorCode.INVALID_PAGINATION);
        }

        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new ApiException(SalesLedgerErrorCode.INVALID_PAGINATION);
        }

        var customer = customerRepository.findByUuid(customerUuid)
            .orElseThrow(() -> new ApiException(SalesLedgerErrorCode.CUSTOMER_NOT_FOUND));

        String normalizedSearch = search == null || search.isBlank()
            ? null
            : search.trim();

        Long customerId = customer.id();

        logger.debug(
            "Fetching sales ledger for customer UUID {} from {} to {}, page {}, size {}",
            customerUuid, fromDate, toDate, page, size
        );

        SalesLedgerSummaryResponse summary =
            salesLedgerRepository.getSummary(customerId, fromDate, toDate);

        var entries = salesLedgerRepository.findEntries(
            customerId, fromDate, toDate, normalizedSearch, size, offset
        );

        long totalElements = salesLedgerRepository.countEntries(
            customerId, fromDate, toDate, normalizedSearch
        );

        int totalPages = totalElements == 0
            ? 0
            : (int) Math.ceil((double) totalElements / size);

        boolean last = totalPages == 0 || page >= totalPages - 1;

        return new SalesLedgerPageResponse(
            summary, entries, page, size, totalElements, totalPages, last
        );
    }
}
