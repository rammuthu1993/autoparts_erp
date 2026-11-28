package com.autoparts.erp.supplier.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.supplier.dto.SupplierResponse;
import com.autoparts.erp.supplier.dto.SupplierSummaryResponse;
import com.autoparts.erp.supplier.dto.SupplierUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface SupplierService {

    SupplierResponse create(SupplierCreateRequest request);

    List<SupplierResponse> findAll();

    List<SupplierResponse> findAllActive();

    default PageResponse<SupplierResponse> findPaged(
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

    PageResponse<SupplierResponse> findPaged(
            int page,
            int size,
            String search,
            String status,
            String city,
            String state,
            String supplierType,
            String sortBy,
            String sortDir
    );

    SupplierResponse findByUuid(UUID uuid);

    SupplierSummaryResponse getSummary(UUID uuid);

    SupplierResponse update(UUID uuid, SupplierUpdateRequest request);

    void deactivate(UUID uuid);

    SupplierResponse reactivate(UUID uuid);
}
