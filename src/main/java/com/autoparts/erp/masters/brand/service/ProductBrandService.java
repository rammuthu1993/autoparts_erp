package com.autoparts.erp.masters.brand.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.brand.dto.BrandUsageResponse;

import java.util.List;
import java.util.UUID;

public interface ProductBrandService {

    BrandResponse create(BrandCreateRequest request);

    List<BrandResponse> findAll();

    List<BrandResponse> findAllActive();

    default PageResponse<BrandResponse> findPaged(
            int page,
            int size,
            String search,
            String sortBy,
            String sortDir
    ) {
        return findPaged(page, size, search, "ALL", sortBy, sortDir);
    }

    PageResponse<BrandResponse> findPaged(
            int page,
            int size,
            String search,
            String status,
            String sortBy,
            String sortDir
    );

    BrandResponse findByUuid(UUID uuid);

    BrandUsageResponse getUsage(UUID uuid);

    BrandResponse update(UUID uuid, BrandUpdateRequest request);

    void deactivate(UUID uuid);

    BrandResponse reactivate(UUID uuid);
}
