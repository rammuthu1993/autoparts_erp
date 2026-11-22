package com.autoparts.erp.masters.category.repository;

import com.autoparts.erp.masters.category.model.ProductCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository {

    ProductCategory create(ProductCategory category);

    List<ProductCategory> findAll();

    List<ProductCategory> findPaged(
        int offset,
        int limit,
        String search,
        String sortBy,
        String sortDir
    );

    long countPaged(String search);

    Optional<ProductCategory> findByUuid(UUID uuid);

    boolean update(UUID uuid, String name, String description);

    boolean deactivate(UUID uuid);
}