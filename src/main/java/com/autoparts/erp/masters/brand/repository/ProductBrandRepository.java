package com.autoparts.erp.masters.brand.repository;

import com.autoparts.erp.masters.brand.dto.BrandUsageResponse;
import com.autoparts.erp.masters.brand.model.ProductBrand;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductBrandRepository {

    ProductBrand create(ProductBrand brand);

    List<ProductBrand> findAll();

    List<ProductBrand> findAllActive();

    default List<ProductBrand> findPaged(
            int offset,
            int limit,
            String search,
            String sortBy,
            String sortDir
    ) {
        return findPaged(offset, limit, search, "ALL", sortBy, sortDir);
    }

    List<ProductBrand> findPaged(
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

    Optional<ProductBrand> findByUuid(UUID uuid);

    Optional<ProductBrand> findById(Long id);

    default boolean update(UUID uuid, String name, String description) {
        return update(uuid, null, name, description, null, null);
    }

    boolean update(UUID uuid, String code, String name, String description, String countryOfOrigin, String website);

    boolean deactivate(UUID uuid);

    boolean reactivate(UUID uuid);

    Optional<ProductBrand> findByNameIgnoreCase(String name);

    Optional<ProductBrand> findByCodeIgnoreCase(String code);

    BrandUsageResponse getUsage(UUID uuid);
}
