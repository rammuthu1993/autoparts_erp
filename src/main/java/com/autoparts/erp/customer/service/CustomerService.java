package com.autoparts.erp.customer.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.customer.dto.CustomerCreateRequest;
import com.autoparts.erp.customer.dto.CustomerDuplicateWarning;
import com.autoparts.erp.customer.dto.CustomerResponse;
import com.autoparts.erp.customer.dto.CustomerSummaryResponse;
import com.autoparts.erp.customer.dto.CustomerUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerCreateRequest request);

    List<CustomerResponse> findAll();

    List<CustomerResponse> findAllActive();

    default PageResponse<CustomerResponse> findPaged(
            int page,
            int size,
            String search,
            String city,
            String state,
            String sortBy,
            String sortDir
    ) {
        return findPaged(page, size, search, "ACTIVE", city, state, null, sortBy, sortDir);
    }

    PageResponse<CustomerResponse> findPaged(
            int page,
            int size,
            String search,
            String status,
            String city,
            String state,
            String customerType,
            String sortBy,
            String sortDir
    );

    CustomerResponse findByUuid(UUID uuid);

    CustomerSummaryResponse getSummary(UUID uuid);

    List<CustomerDuplicateWarning> checkDuplicates(
            String phone,
            String email,
            String gstin,
            UUID excludeUuid
    );

    CustomerResponse update(
            UUID uuid,
            CustomerUpdateRequest request
    );

    void deactivate(UUID uuid);

    CustomerResponse reactivate(UUID uuid);
}
