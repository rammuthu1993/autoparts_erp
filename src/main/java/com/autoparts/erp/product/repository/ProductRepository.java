
package com.autoparts.erp.product.repository;

import com.autoparts.erp.product.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    long nextItemNumber();

    boolean categoryExistsAndActive(Long categoryId);

    boolean brandExistsAndActive(Long brandId);

    Product create(
            UUID uuid,
            String itemId,
            String partNo,
            String name,
            String description,
            Long categoryId,
            Long brandId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            int qty,
            BigDecimal minimumStock,
            String unit
    );

    List<Product> findAll();

    Optional<Product> findByUuid(UUID uuid);

    boolean update(
            UUID uuid,
            String partNo,
            String name,
            String description,
            Long categoryId,
            Long brandId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal minimumStock,
            String unit
    );

    boolean deactivate(UUID uuid);
    
    boolean existsByName(String name);

    boolean existsByPartNo(String partNo);

    boolean existsByItemId(String itemId);
}
