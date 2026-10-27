package com.autoparts.erp.masters.brand.service;

import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface ProductBrandService {

    BrandResponse create(BrandCreateRequest request);

    List<BrandResponse> findAll();

    BrandResponse findByUuid(UUID uuid);

    BrandResponse update(UUID uuid, BrandUpdateRequest request);

    void deactivate(UUID uuid);
}