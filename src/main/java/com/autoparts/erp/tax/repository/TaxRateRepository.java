package com.autoparts.erp.tax.repository;

import com.autoparts.erp.tax.dto.TaxSummaryResponse;
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

    Optional<TaxRate> findCurrentByGstRate(BigDecimal gstRate, LocalDate onDate);

    List<TaxRate> findAll();

    default List<TaxRate> findPaged(
        int offset,
        int limit,
        String search,
        String sortBy,
        String sortDir
    ) {
        return findPaged(offset, limit, search, null, null, null, null, sortBy, sortDir);
    }

    List<TaxRate> findPaged(
        int offset,
        int limit,
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn,
        String sortBy,
        String sortDir
    );

    default long countPaged(String search) {
        return countPaged(search, null, null, null, null);
    }

    long countPaged(
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn
    );

    List<TaxRate> findCurrent();

    List<TaxRate> findSelectable(LocalDate effectiveDate);

    TaxSummaryResponse getSummary();

    boolean existsByNameAndEffectiveFrom(
        String taxName,
        LocalDate effectiveFrom,
        UUID excludeUuid
    );

    default TaxRate update(UUID uuid, String taxName, boolean active) {
        return update(uuid, taxName, active, null);
    }

    TaxRate update(
        UUID uuid,
        String taxName,
        boolean active,
        String changeReason
    );

    TaxRate closePeriod(
        UUID uuid,
        LocalDate effectiveTo,
        String changeReason
    );
}
