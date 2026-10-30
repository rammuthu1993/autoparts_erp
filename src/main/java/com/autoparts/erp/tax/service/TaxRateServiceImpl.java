
package com.autoparts.erp.tax.service;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.model.TaxErrorCode;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.TaxRateRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaxRateServiceImpl implements TaxRateService {

    private final TaxRateRepository taxRateRepository;

    public TaxRateServiceImpl(
        TaxRateRepository taxRateRepository
    ) {
        this.taxRateRepository = taxRateRepository;
    }

    @Override
    @Transactional
    public TaxRateResponse create(
        TaxRateCreateRequest request
    ) {

        validateTaxRates(
            request.gstRate(),
            request.cgstRate(),
            request.sgstRate(),
            request.igstRate()
        );

        validateEffectiveDates(
            request.effectiveFrom(),
            request.effectiveTo()
        );

        // Serialize tax configuration changes.
        taxRateRepository.lockTaxRateConfiguration();

        if (taxRateRepository.existsOverlappingTaxRate(
            request.taxName(),
            request.effectiveFrom(),
            request.effectiveTo(),
            null
        )) {
            throw new ApiException(
                TaxErrorCode.TAX_RATE_ALREADY_EXISTS
            );
        }

        try {

            UUID uuid = UUID.randomUUID();

            TaxRate taxRate = taxRateRepository.create(
                uuid,
                request.taxName(),
                request.gstRate(),
                request.cgstRate(),
                request.sgstRate(),
                request.igstRate(),
                request.effectiveFrom(),
                request.effectiveTo()
            );

            return toResponse(taxRate);

        } catch (DuplicateKeyException exception) {

            throw new ApiException(
                TaxErrorCode.TAX_RATE_ALREADY_EXISTS
            );
        }
    }

    @Override
    public List<TaxRateResponse> findAll() {

        return taxRateRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public TaxRateResponse findByUuid(UUID uuid) {

        TaxRate taxRate = taxRateRepository
            .findByUuid(uuid)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.TAX_RATE_NOT_FOUND
            ));

        return toResponse(taxRate);
    }

    @Override
    @Transactional
    public TaxRateResponse update(
        UUID uuid,
        TaxRateUpdateRequest request
    ) {

        TaxRate existing = taxRateRepository
            .findByUuid(uuid)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.TAX_RATE_NOT_FOUND
            ));

        taxRateRepository.lockTaxRateConfiguration();

        if (taxRateRepository.existsByNameAndEffectiveFrom(
            request.taxName(),
            existing.effectiveFrom(),
            uuid
        )) {
            throw new ApiException(
                TaxErrorCode.TAX_RATE_ALREADY_EXISTS
            );
        }

        if (taxRateRepository.existsOverlappingTaxRate(
            request.taxName(),
            existing.effectiveFrom(),
            existing.effectiveTo(),
            uuid
        )) {
            throw new ApiException(
                TaxErrorCode.TAX_RATE_ALREADY_EXISTS
            );
        }

        try {

            TaxRate updated = taxRateRepository.update(
                uuid,
                request.taxName(),
                request.active()
            );

            return toResponse(updated);

        } catch (DuplicateKeyException exception) {

            throw new ApiException(
                TaxErrorCode.TAX_RATE_ALREADY_EXISTS
            );
        }
    }

    @Override
    public List<TaxRateResponse> findCurrent() {

        return taxRateRepository.findCurrent()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private void validateTaxRates(
        BigDecimal gstRate,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal igstRate
    ) {

        BigDecimal componentTotal = cgstRate.add(sgstRate);

        if (gstRate.compareTo(componentTotal) != 0
            || gstRate.compareTo(igstRate) != 0) {

            throw new ApiException(
                TaxErrorCode.INVALID_TAX_RATE
            );
        }
    }

    private void validateEffectiveDates(
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {

        if (effectiveTo != null
            && !effectiveTo.isAfter(effectiveFrom)) {

            throw new ApiException(
                TaxErrorCode.INVALID_TAX_EFFECTIVE_DATE
            );
        }
    }

    private TaxRateResponse toResponse(TaxRate taxRate) {

        return new TaxRateResponse(
            taxRate.uuid(),
            taxRate.taxName(),
            taxRate.gstRate(),
            taxRate.cgstRate(),
            taxRate.sgstRate(),
            taxRate.igstRate(),
            taxRate.effectiveFrom(),
            taxRate.effectiveTo(),
            taxRate.active(),
            taxRate.createdAt(),
            taxRate.updatedAt()
        );
    }
}
