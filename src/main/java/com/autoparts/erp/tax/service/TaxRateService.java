
package com.autoparts.erp.tax.service;

import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface TaxRateService {

    TaxRateResponse create(TaxRateCreateRequest request);

    List<TaxRateResponse> findAll();

    TaxRateResponse findByUuid(UUID uuid);

    TaxRateResponse update(
        UUID uuid,
        TaxRateUpdateRequest request
    );

    List<TaxRateResponse> findCurrent();
}
