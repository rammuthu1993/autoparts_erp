package com.autoparts.erp.customer.repository;

import com.autoparts.erp.customer.dto.CustomerDuplicateWarning;
import com.autoparts.erp.customer.model.Customer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    record CustomerActivityStats(
            long totalSalesCount,
            LocalDate lastSaleDate,
            LocalDate lastPaymentDate
    ) {}

    Customer create(Customer customer);

    List<Customer> findAll();

    List<Customer> findAllActive();

    default List<Customer> findPaged(
            int offset,
            int limit,
            String search,
            String city,
            String state,
            String sortBy,
            String sortDir
    ) {
        return findPaged(offset, limit, search, "ACTIVE", city, state, null, sortBy, sortDir);
    }

    List<Customer> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String city,
            String state,
            String customerType,
            String sortBy,
            String sortDir
    );

    default long countPaged(
            String search,
            String city,
            String state
    ) {
        return countPaged(search, "ACTIVE", city, state, null);
    }

    long countPaged(
            String search,
            String status,
            String city,
            String state,
            String customerType
    );

    Optional<Customer> findByUuid(UUID uuid);

    Optional<Customer> findById(Long id);

    boolean update(Customer customer);

    boolean deactivate(Long id);

    boolean reactivate(Long id);

    boolean existsByGstinExcludingUuid(String gstin, UUID excludeUuid);

    List<CustomerDuplicateWarning> findPotentialDuplicates(
            String phone,
            String email,
            String gstin,
            UUID excludeUuid
    );

    CustomerActivityStats getActivityStats(Long customerId);
}
