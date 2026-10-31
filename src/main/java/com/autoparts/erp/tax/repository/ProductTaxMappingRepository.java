
package com.autoparts.erp.tax.repository;

import com.autoparts.erp.tax.model.ProductTaxMapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductTaxMappingRepository {

    Optional<Long> lockActiveProduct(UUID productUuid);

    boolean existsOverlappingMapping(
        Long productId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    );

    ProductTaxMapping create(
        UUID uuid,
        Long productId,
        Long taxRateId,
        String hsnCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    );

    Optional<ProductTaxMapping> findByUuid(UUID uuid);

    List<ProductTaxMapping> findByProductUuid(UUID productUuid);

    Optional<ProductTaxMapping> findCurrentByProductUuid(
        UUID productUuid
    );
    
    Optional<UUID> findProductUuidById(Long productId);
}
