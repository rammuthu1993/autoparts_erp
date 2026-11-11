package com.autoparts.erp.product.repository;

import com.autoparts.erp.product.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    long nextProductCodeNumber();

    boolean categoryExistsAndActive(Long categoryId);

    boolean brandExistsAndActive(Long brandId);

    Product create(
            UUID uuid,
            String productCode,
            String partNo,
            String name,
            String description,
            Long categoryId,
            Long brandId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal minimumStock,
            String unit,
            String barcode,
            String barcodeType
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

    boolean existsByProductCode(String productCode);

    boolean existsByNameExcludingUuid(String name, UUID uuid);

    boolean existsByPartNoExcludingUuid(String partNo, UUID uuid);
}
