package com.autoparts.erp.masters.brand.repository;

import com.autoparts.erp.masters.brand.model.ProductBrand;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductBrandRepository {

    ProductBrand create(ProductBrand brand);

    List<ProductBrand> findAll();

    Optional<ProductBrand> findByUuid(UUID uuid);

    boolean update(UUID uuid, String name, String description);

    boolean deactivate(UUID uuid);
    
    public Optional<ProductBrand> findByNameIgnoreCase(String name);
}