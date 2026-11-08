
package com.autoparts.erp.tax.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.model.TaxErrorCode;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.TaxRateRepository;

import com.autoparts.erp.tax.service.TaxRateService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaxRateServiceImpl implements TaxRateService {

    private final TaxRateRepository taxRateRepository;
    private final AuditLogService auditLogService;

    public TaxRateServiceImpl(
        TaxRateRepository taxRateRepository,
        AuditLogService auditLogService
    ) {
        this.taxRateRepository = taxRateRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOnly
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
                TaxErrorCode.TAX_RATE_OVERLAPPING_PERIOD
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

            // Audit successful tax rate creation.
            auditLogService.log(
                "CREATE",
                "TAX_RATE",
                taxRate.uuid().toString(),
                "Created tax rate: " + taxRate.taxName(),
                null,
                toAuditValues(taxRate)
            );

            return toResponse(taxRate);

        } catch (DuplicateKeyException exception) {

            throw new ApiException(
                TaxErrorCode.TAX_RATE_DUPLICATE_CONSTRAINT
            );
        }
    }

    @Override
    @SalesAccess
    public List<TaxRateResponse> findAll() {

        return taxRateRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @SalesAccess
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
    @AdminOnly
    public TaxRateResponse update(
        UUID uuid,
        TaxRateUpdateRequest request
    ) {

        TaxRate existing = taxRateRepository
            .findByUuid(uuid)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.TAX_RATE_NOT_FOUND
            ));

        // Capture previous values before updating.
        Map<String, Object> oldValues = toAuditValues(existing);

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

            // Audit successful tax rate update.
            auditLogService.log(
                "UPDATE",
                "TAX_RATE",
                updated.uuid().toString(),
                "Updated tax rate: " + updated.taxName(),
                oldValues,
                toAuditValues(updated)
            );

            return toResponse(updated);

        } catch (DuplicateKeyException exception) {

            throw new ApiException(
                TaxErrorCode.TAX_RATE_ALREADY_EXISTS
            );
        }
    }

    @Override
    @SalesAccess
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

    private Map<String, Object> toAuditValues(
        TaxRate taxRate
    ) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("taxName", taxRate.taxName());
        values.put("gstRate", taxRate.gstRate());
        values.put("cgstRate", taxRate.cgstRate());
        values.put("sgstRate", taxRate.sgstRate());
        values.put("igstRate", taxRate.igstRate());
        values.put("effectiveFrom", taxRate.effectiveFrom());
        values.put("effectiveTo", taxRate.effectiveTo());
        values.put("active", taxRate.active());

        return values;
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
