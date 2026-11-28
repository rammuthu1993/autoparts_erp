package com.autoparts.erp.tax.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.tax.dto.TaxRateCloseRequest;
import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateSupersedeRequest;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.dto.TaxSummaryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaxRateService {

    TaxRateResponse create(TaxRateCreateRequest request);

    List<TaxRateResponse> findAll();

    default PageResponse<TaxRateResponse> findPaged(
        int page,
        int size,
        String search,
        String sortBy,
        String sortDir
    ) {
        return findPaged(page, size, search, null, null, null, null, sortBy, sortDir);
    }

    PageResponse<TaxRateResponse> findPaged(
        int page,
        int size,
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn,
        String sortBy,
        String sortDir
    );

    TaxRateResponse findByUuid(UUID uuid);

    TaxRateResponse update(
        UUID uuid,
        TaxRateUpdateRequest request
    );

    TaxRateResponse closePeriod(
        UUID uuid,
        TaxRateCloseRequest request
    );

    TaxRateResponse supersede(
        UUID uuid,
        TaxRateSupersedeRequest request
    );

    List<TaxRateResponse> findCurrent();

    List<TaxRateResponse> findSelectable(LocalDate effectiveDate);

    TaxSummaryResponse getSummary();
}
