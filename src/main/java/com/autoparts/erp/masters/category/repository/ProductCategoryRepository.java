package com.autoparts.erp.masters.category.repository;

import com.autoparts.erp.masters.category.dto.CategoryUsageResponse;
import com.autoparts.erp.masters.category.model.ProductCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository {

    ProductCategory create(ProductCategory category);

    List<ProductCategory> findAll();

    List<ProductCategory> findAllActive();

    default List<ProductCategory> findPaged(
            int offset,
            int limit,
            String search,
            String sortBy,
            String sortDir
    ) {
        return findPaged(offset, limit, search, "ALL", sortBy, sortDir);
    }

    List<ProductCategory> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String sortBy,
            String sortDir
    );

    default long countPaged(String search) {
        return countPaged(search, "ALL");
    }

    long countPaged(String search, String status);

    Optional<ProductCategory> findByUuid(UUID uuid);

    Optional<ProductCategory> findById(Long id);

    Optional<ProductCategory> findByNameIgnoreCase(String name);

    Optional<ProductCategory> findByCodeIgnoreCase(String code);

    default boolean update(UUID uuid, String name, String description) {
        return update(uuid, null, name, description, null);
    }

    boolean update(UUID uuid, String code, String name, String description, Long parentId);

    boolean deactivate(UUID uuid);

    boolean reactivate(UUID uuid);

    CategoryUsageResponse getUsage(UUID uuid);
}
