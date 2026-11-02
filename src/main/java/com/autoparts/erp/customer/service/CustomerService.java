package com.autoparts.erp.customer.service;

import com.autoparts.erp.customer.dto.CustomerCreateRequest;
import com.autoparts.erp.customer.dto.CustomerResponse;
import com.autoparts.erp.customer.dto.CustomerUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerCreateRequest request);

    List<CustomerResponse> findAll();

    CustomerResponse findByUuid(UUID uuid);

    CustomerResponse update(
            UUID uuid,
            CustomerUpdateRequest request
    );

    void deactivate(UUID uuid);
}