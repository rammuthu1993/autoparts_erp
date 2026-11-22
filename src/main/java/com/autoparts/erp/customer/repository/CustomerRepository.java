package com.autoparts.erp.customer.repository;

import com.autoparts.erp.customer.model.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Customer create(Customer customer);

    List<Customer> findAll();

    List<Customer> findPaged(
            int offset,
            int limit,
            String search,
            String city,
            String state,
            String sortBy,
            String sortDir
    );

    long countPaged(
            String search,
            String city,
            String state
    );

    Optional<Customer> findByUuid(UUID uuid);

    Optional<Customer> findById(Long id);

    boolean update(Customer customer);

    boolean deactivate(Long id);
}