package com.autoparts.erp.masters.category.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryResponse;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryService {

    CategoryResponse create(CategoryCreateRequest request);

    List<CategoryResponse> findAll();

    PageResponse<CategoryResponse> findPaged(
        int page,
        int size,
        String search,
        String sortBy,
        String sortDir
    );

    CategoryResponse findByUuid(UUID uuid);

    CategoryResponse update(UUID uuid, CategoryUpdateRequest request);

    void deactivate(UUID uuid);
}