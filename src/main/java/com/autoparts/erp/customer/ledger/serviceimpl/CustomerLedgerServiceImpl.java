
package com.autoparts.erp.customer.ledger.serviceimpl;

import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerEntry;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerResponse;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;
import com.autoparts.erp.customer.ledger.repository.CustomerLedgerRepository;
import com.autoparts.erp.customer.ledger.service.CustomerLedgerService;
import com.autoparts.erp.customer.exception.CustomerErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerLedgerServiceImpl implements CustomerLedgerService {

    private final CustomerLedgerRepository repository;

    public CustomerLedgerServiceImpl(
        CustomerLedgerRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @SalesAccess
    public CustomerLedgerResponse getCustomerLedger(
        UUID customerUuid,
        LocalDate fromDate,
        LocalDate toDate,
        int page,
        int size
    ) {

        validateFilters(fromDate, toDate, page, size);

        CustomerLedgerRepository.CustomerInfo customer =
            repository.findCustomerByUuid(customerUuid)
                .orElseThrow(() -> new ApiException(
                    CustomerErrorCode.CUSTOMER_LEDGER_NOT_FOUND
                ));

        BigDecimal openingBalance = repository.getOpeningBalance(
            customer.id(),
            fromDate
        );

        CustomerLedgerSummary summary = repository.getSummary(
            customer,
            fromDate,
            toDate,
            openingBalance
        );

        long totalElements = repository.countEntries(
            customer.id(),
            fromDate,
            toDate
        );

        int offset = Math.multiplyExact(page, size);

        List<CustomerLedgerEntry> entries = repository.findEntries(
            customer.id(),
            fromDate,
            toDate,
            openingBalance,
            size,
            offset
        );

        int totalPages = totalElements == 0
            ? 0
            : (int) Math.ceil((double) totalElements / size);

        return new CustomerLedgerResponse(
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

        if (fromDate != null && toDate != null
            && fromDate.isAfter(toDate)) {

            throw new ApiException(
                CustomerErrorCode.INVALID_CUSTOMER_LEDGER_FILTER
            );
        }

        if (page < 0 || size < 1 || size > 100
            || (long) page * size > Integer.MAX_VALUE) {

            throw new ApiException(
                CustomerErrorCode.INVALID_CUSTOMER_LEDGER_FILTER
            );
        }
    }
}
