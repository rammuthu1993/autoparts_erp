package com.autoparts.erp.masters.category.repository;

import com.autoparts.erp.masters.category.model.ProductCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository {

    ProductCategory create(ProductCategory category);

    List<ProductCategory> findAll();

    Optional<ProductCategory> findByUuid(UUID uuid);

    boolean update(UUID uuid, String name, String description);

    boolean deactivate(UUID uuid);
}