package com.autoparts.erp.tax.repository;

import com.autoparts.erp.tax.model.ProductTaxMapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductTaxMappingRepository {

    Optional<Long> lockActiveProduct(UUID productUuid);

    default boolean existsOverlappingMapping(
        Long productId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        return existsOverlappingMapping(productId, effectiveFrom, effectiveTo, null);
    }

    boolean existsOverlappingMapping(
        Long productId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID excludeMappingUuid
    );

    default ProductTaxMapping create(
        UUID uuid,
        Long productId,
        Long taxRateId,
        String hsnCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        return create(uuid, productId, taxRateId, hsnCode, null, effectiveFrom, effectiveTo, null, null);
    }

    ProductTaxMapping create(
        UUID uuid,
        Long productId,
        Long taxRateId,
        String hsnCode,
        String hsnDescription,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String changeReason,
        String createdBy
    );

    Optional<ProductTaxMapping> findByUuid(UUID uuid);

    List<ProductTaxMapping> findByProductUuid(UUID productUuid);

    List<ProductTaxMapping> findPagedByProductUuid(
        UUID productUuid,
        int offset,
        int limit,
        LocalDate fromDate,
        LocalDate toDate
    );

    long countPagedByProductUuid(
        UUID productUuid,
        LocalDate fromDate,
        LocalDate toDate
    );

    Optional<ProductTaxMapping> findCurrentByProductUuid(UUID productUuid);

    Optional<ProductTaxMapping> findEffectiveOnDate(Long productId, LocalDate targetDate);

    Optional<ProductTaxMapping> findMappingByProductUuidAndDate(
        UUID productUuid,
        LocalDate saleDate
    );

    ProductTaxMapping closePeriod(
        UUID mappingUuid,
        LocalDate effectiveTo,
        String changeReason
    );

    Optional<UUID> findProductUuidById(Long productId);
}
