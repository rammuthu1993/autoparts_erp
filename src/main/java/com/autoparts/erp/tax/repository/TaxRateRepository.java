package com.autoparts.erp.tax.repository;

import com.autoparts.erp.tax.model.TaxRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRateRepository {

    void lockTaxRateConfiguration();

    boolean existsOverlappingTaxRate(
        String taxName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID excludeUuid
    );

    TaxRate create(
        UUID uuid,
        String taxName,
        BigDecimal gstRate,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal igstRate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    );

    Optional<TaxRate> findByUuid(UUID uuid);

    List<TaxRate> findAll();

    List<TaxRate> findCurrent();

    boolean existsByNameAndEffectiveFrom(
        String taxName,
        LocalDate effectiveFrom,
        UUID excludeUuid
    );

    TaxRate update(
        UUID uuid,
        String taxName,
        boolean active
    );
}
