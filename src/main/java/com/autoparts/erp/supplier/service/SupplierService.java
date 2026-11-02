package com.autoparts.erp.supplier.service;

import com.autoparts.erp.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.supplier.dto.SupplierResponse;
import com.autoparts.erp.supplier.dto.SupplierUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface SupplierService {

    SupplierResponse create(SupplierCreateRequest request);

    List<SupplierResponse> findAll();

    SupplierResponse findByUuid(UUID uuid);

    SupplierResponse update(UUID uuid, SupplierUpdateRequest request);

    void deactivate(UUID uuid);
}
