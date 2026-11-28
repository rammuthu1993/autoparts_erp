package com.autoparts.erp.masters.category.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryResponse;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;
import com.autoparts.erp.masters.category.dto.CategoryUsageResponse;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryService {

    CategoryResponse create(CategoryCreateRequest request);

    List<CategoryResponse> findAll();

    List<CategoryResponse> findAllActive();

    default PageResponse<CategoryResponse> findPaged(
            int page,
            int size,
            String search,
            String sortBy,
            String sortDir
    ) {
        return findPaged(page, size, search, "ALL", sortBy, sortDir);
    }

    PageResponse<CategoryResponse> findPaged(
            int page,
            int size,
            String search,
            String status,
            String sortBy,
            String sortDir
    );

    CategoryResponse findByUuid(UUID uuid);

    CategoryUsageResponse getUsage(UUID uuid);

    CategoryResponse update(UUID uuid, CategoryUpdateRequest request);

    void deactivate(UUID uuid);

    CategoryResponse reactivate(UUID uuid);
}
