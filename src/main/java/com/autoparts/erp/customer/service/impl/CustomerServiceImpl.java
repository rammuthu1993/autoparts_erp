
package com.autoparts.erp.customer.service.impl;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.customer.dto.CustomerCreateRequest;
import com.autoparts.erp.customer.dto.CustomerResponse;
import com.autoparts.erp.customer.dto.CustomerUpdateRequest;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.exception.CustomerErrorCode;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.customer.service.CustomerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log =
        LoggerFactory.getLogger(CustomerServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public CustomerServiceImpl(
        CustomerRepository customerRepository,
        AuditLogService auditLogService
    ) {
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
    public CustomerResponse create(CustomerCreateRequest request) {

        Customer customer = new Customer(
            null,
            UUID.randomUUID(),
            null,
            request.customerName().trim(),
            clean(request.phone()),
            clean(request.email()),
            clean(request.address()),
            clean(request.city()),
            clean(request.state()),
            clean(request.pincode()),
            normalizeGstin(request.gstin()),
            true,
            null,
            null
        );

        Customer saved = customerRepository.create(customer);

        auditLogService.log(
            "CREATE",
            "CUSTOMER",
            saved.uuid().toString(),
            "Customer created: " + saved.customerCode(),
            null,
            toAuditValues(saved)
        );

        log.info(
            "Customer created successfully. Customer code: {}",
            saved.customerCode()
        );

        return toResponse(saved);
    }

    @Override
    @SalesAccess
    public List<CustomerResponse> findAll() {

        return customerRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @SalesAccess
    public CustomerResponse findByUuid(UUID uuid) {

        Customer customer = customerRepository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(
                    CustomerErrorCode.CUSTOMER_NOT_FOUND
                )
            );

        return toResponse(customer);
    }

    @Override
    @Transactional
    @AdminOrManager
    public CustomerResponse update(
        UUID uuid,
        CustomerUpdateRequest request
    ) {

        Customer existing = customerRepository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(
                    CustomerErrorCode.CUSTOMER_NOT_FOUND
                )
            );

        Customer updated = new Customer(
            existing.id(),
            existing.uuid(),
            existing.customerCode(),
            request.customerName().trim(),
            clean(request.phone()),
            clean(request.email()),
            clean(request.address()),
            clean(request.city()),
            clean(request.state()),
            clean(request.pincode()),
            normalizeGstin(request.gstin()),
            existing.active(),
            existing.createdAt(),
            existing.updatedAt()
        );

        customerRepository.update(updated);

        Customer saved = customerRepository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(
                    CustomerErrorCode.CUSTOMER_NOT_FOUND
                )
            );

        auditLogService.log(
            "UPDATE",
            "CUSTOMER",
            saved.uuid().toString(),
            "Customer updated: " + saved.customerCode(),
            toAuditValues(existing),
            toAuditValues(saved)
        );

        log.info(
            "Customer updated successfully. Customer code: {}",
            saved.customerCode()
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    @AdminOnly
    public void deactivate(UUID uuid) {

        Customer customer = customerRepository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(
                    CustomerErrorCode.CUSTOMER_NOT_FOUND
                )
            );

        boolean deactivated =
            customerRepository.deactivate(customer.id());

        if (!deactivated) {
            throw new ApiException(
                CustomerErrorCode.CUSTOMER_ALREADY_INACTIVE
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("customerCode", customer.customerCode());
        newValues.put("customerName", customer.customerName());
        newValues.put("active", false);

        auditLogService.log(
            "DELETE",
            "CUSTOMER",
            customer.uuid().toString(),
            "Customer deactivated: " + customer.customerCode(),
            toAuditValues(customer),
            newValues
        );

        log.info(
            "Customer deactivated. Customer code: {}",
            customer.customerCode()
        );
    }

    private CustomerResponse toResponse(Customer customer) {

        return new CustomerResponse(
            customer.uuid(),
            customer.customerCode(),
            customer.customerName(),
            customer.phone(),
            customer.email(),
            customer.address(),
            customer.city(),
            customer.state(),
            customer.pincode(),
            customer.gstin(),
            customer.active(),
            customer.createdAt(),
            customer.updatedAt()
        );
    }

    /**
     * Avoid storing customer contact details and GSTIN
     * unnecessarily in audit logs.
     */
    private Map<String, Object> toAuditValues(Customer customer) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("customerCode", customer.customerCode());
        values.put("customerName", customer.customerName());
        values.put("active", customer.active());

        return values;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeGstin(String gstin) {
        String value = clean(gstin);
        return value == null ? null : value.toUpperCase();
    }
}
