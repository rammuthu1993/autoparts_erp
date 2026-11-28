package com.autoparts.erp.supplier.repository;

import com.autoparts.erp.supplier.model.Supplier;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    record SupplierActivityStats(
            long totalPurchasesCount,
            LocalDate lastPurchaseDate,
            LocalDate lastPaymentDate
    ) {}

    Supplier create(Supplier supplier);

    List<Supplier> findAll();

    List<Supplier> findAllActive();

    default List<Supplier> findPaged(
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

    List<Supplier> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String city,
            String state,
            String supplierType,
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
            String supplierType
    );

    Optional<Supplier> findByUuid(UUID uuid);

    boolean update(UUID uuid, Supplier supplier);

    boolean deactivate(UUID uuid);

    boolean reactivate(UUID uuid);

    boolean existsBySupplierName(String supplierName);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByGstin(String gstin);

    boolean existsBySupplierNameExcludingUuid(String supplierName, UUID uuid);

    boolean existsByPhoneExcludingUuid(String phone, UUID uuid);

    boolean existsByEmailExcludingUuid(String email, UUID uuid);

    boolean existsByGstinExcludingUuid(String gstin, UUID uuid);

    SupplierActivityStats getActivityStats(Long supplierId);
}
