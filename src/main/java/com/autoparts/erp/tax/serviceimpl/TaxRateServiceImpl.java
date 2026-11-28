package com.autoparts.erp.tax.serviceimpl;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.tax.dto.TaxRateCloseRequest;
import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateSupersedeRequest;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.dto.TaxSummaryResponse;
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
    @RequiresPermission("TAX_RATES.CREATE")
    public TaxRateResponse create(TaxRateCreateRequest request) {
        validateTaxRates(
            request.gstRate(),
            request.cgstRate(),
            request.sgstRate(),
            request.igstRate()
        );
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());

        taxRateRepository.lockTaxRateConfiguration();

        if (taxRateRepository.existsOverlappingTaxRate(
            request.taxName(),
            request.effectiveFrom(),
            request.effectiveTo(),
            null
        )) {
            throw new ApiException(TaxErrorCode.TAX_RATE_OVERLAPPING_PERIOD);
        }

        try {
            TaxRate taxRate = taxRateRepository.create(
                UUID.randomUUID(),
                request.taxName(),
                request.gstRate(),
                request.cgstRate(),
                request.sgstRate(),
                request.igstRate(),
                request.effectiveFrom(),
                request.effectiveTo()
            );

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
            throw new ApiException(TaxErrorCode.TAX_RATE_DUPLICATE_CONSTRAINT);
        }
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public List<TaxRateResponse> findAll() {
        return taxRateRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public PageResponse<TaxRateResponse> findPaged(
        int page,
        int size,
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn,
        String sortBy,
        String sortDir
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 20 : Math.min(size, 100);
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<TaxRateResponse> content = taxRateRepository
            .findPaged(offset, safeSize, search, gstRate, active, periodStatus, effectiveOn, sortBy, sortDir)
            .stream()
            .map(this::toResponse)
            .toList();

        long total = taxRateRepository.countPaged(search, gstRate, active, periodStatus, effectiveOn);

        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public TaxRateResponse findByUuid(UUID uuid) {
        TaxRate taxRate = taxRateRepository.findByUuid(uuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));
        return toResponse(taxRate);
    }

    @Override
    @Transactional
    @AdminOnly
    @RequiresPermission("TAX_RATES.UPDATE")
    public TaxRateResponse update(UUID uuid, TaxRateUpdateRequest request) {
        taxRateRepository.lockTaxRateConfiguration();

        TaxRate existing = taxRateRepository.findByUuid(uuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));

        Map<String, Object> oldValues = toAuditValues(existing);

        if (taxRateRepository.existsByNameAndEffectiveFrom(request.taxName(), existing.effectiveFrom(), uuid)) {
            throw new ApiException(TaxErrorCode.TAX_RATE_ALREADY_EXISTS);
        }

        if (taxRateRepository.existsOverlappingTaxRate(
            request.taxName(),
            existing.effectiveFrom(),
            existing.effectiveTo(),
            uuid
        )) {
            throw new ApiException(TaxErrorCode.TAX_RATE_ALREADY_EXISTS);
        }

        try {
            TaxRate updated = taxRateRepository.update(
                uuid,
                request.taxName(),
                request.active(),
                request.changeReason()
            );

            auditLogService.log(
                "UPDATE",
                "TAX_RATE",
                updated.uuid().toString(),
                (!request.active() && existing.active())
                    ? "Deactivated tax rate: " + updated.taxName()
                    : "Updated tax rate: " + updated.taxName(),
                oldValues,
                toAuditValues(updated)
            );

            return toResponse(updated);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(TaxErrorCode.TAX_RATE_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional
    @AdminOnly
    @RequiresPermission("TAX_RATES.UPDATE")
    public TaxRateResponse closePeriod(UUID uuid, TaxRateCloseRequest request) {
        taxRateRepository.lockTaxRateConfiguration();

        TaxRate existing = taxRateRepository.findByUuid(uuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));

        if (!request.effectiveTo().isAfter(existing.effectiveFrom())) {
            throw new ApiException(TaxErrorCode.INVALID_TAX_EFFECTIVE_DATE);
        }
        if (existing.effectiveTo() != null && !request.effectiveTo().isBefore(existing.effectiveTo())) {
            throw new ApiException(TaxErrorCode.INVALID_TAX_EFFECTIVE_DATE);
        }

        Map<String, Object> oldValues = toAuditValues(existing);
        TaxRate closed = taxRateRepository.closePeriod(uuid, request.effectiveTo(), request.changeReason());

        auditLogService.log(
            "UPDATE",
            "TAX_RATE",
            closed.uuid().toString(),
            "Closed effective period for tax rate " + closed.taxName() + " at " + request.effectiveTo()
                + " (" + request.changeReason().trim() + ")",
            oldValues,
            toAuditValues(closed)
        );

        return toResponse(closed);
    }

    @Override
    @Transactional
    @AdminOnly
    @RequiresPermission("TAX_RATES.UPDATE")
    public TaxRateResponse supersede(UUID uuid, TaxRateSupersedeRequest request) {
        validateTaxRates(request.gstRate(), request.cgstRate(), request.sgstRate(), request.igstRate());
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());

        taxRateRepository.lockTaxRateConfiguration();

        TaxRate existing = taxRateRepository.findByUuid(uuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));

        if (!request.effectiveFrom().isAfter(existing.effectiveFrom())) {
            throw new ApiException(TaxErrorCode.INVALID_TAX_EFFECTIVE_DATE);
        }
        if (existing.effectiveTo() != null && request.effectiveFrom().isAfter(existing.effectiveTo())) {
            throw new ApiException(TaxErrorCode.INVALID_TAX_EFFECTIVE_DATE);
        }

        Map<String, Object> oldExistingValues = toAuditValues(existing);
        TaxRate closed = taxRateRepository.closePeriod(uuid, request.effectiveFrom(), request.changeReason());

        if (taxRateRepository.existsOverlappingTaxRate(
            request.taxName(),
            request.effectiveFrom(),
            request.effectiveTo(),
            null
        )) {
            throw new ApiException(TaxErrorCode.TAX_RATE_OVERLAPPING_PERIOD);
        }

        try {
            TaxRate replacement = taxRateRepository.create(
                UUID.randomUUID(),
                request.taxName(),
                request.gstRate(),
                request.cgstRate(),
                request.sgstRate(),
                request.igstRate(),
                request.effectiveFrom(),
                request.effectiveTo()
            );

            auditLogService.log(
                "UPDATE",
                "TAX_RATE",
                closed.uuid().toString(),
                "Superseded tax rate " + closed.taxName() + " effective " + request.effectiveFrom()
                    + " (" + request.changeReason().trim() + ")",
                oldExistingValues,
                toAuditValues(closed)
            );
            auditLogService.log(
                "CREATE",
                "TAX_RATE",
                replacement.uuid().toString(),
                "Created replacement tax rate " + replacement.taxName() + " superseding " + closed.uuid(),
                null,
                toAuditValues(replacement)
            );

            return toResponse(replacement);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(TaxErrorCode.TAX_RATE_DUPLICATE_CONSTRAINT);
        }
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public List<TaxRateResponse> findCurrent() {
        return taxRateRepository.findCurrent().stream().map(this::toResponse).toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public List<TaxRateResponse> findSelectable(LocalDate effectiveDate) {
        return taxRateRepository.findSelectable(effectiveDate).stream().map(this::toResponse).toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public TaxSummaryResponse getSummary() {
        return taxRateRepository.getSummary();
    }

    private void validateTaxRates(
        BigDecimal gstRate,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal igstRate
    ) {
        BigDecimal componentTotal = cgstRate.add(sgstRate);
        if (gstRate.compareTo(componentTotal) != 0 || gstRate.compareTo(igstRate) != 0) {
            throw new ApiException(TaxErrorCode.INVALID_TAX_RATE);
        }
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new ApiException(TaxErrorCode.INVALID_TAX_EFFECTIVE_DATE);
        }
    }

    private String computePeriodStatus(TaxRate taxRate) {
        if (!taxRate.active()) {
            return "INACTIVE";
        }
        LocalDate today = LocalDate.now();
        if (taxRate.effectiveFrom().isAfter(today)) {
            return "UPCOMING";
        }
        if (taxRate.effectiveTo() != null && !taxRate.effectiveTo().isAfter(today)) {
            return "EXPIRED";
        }
        return "CURRENT";
    }

    private Map<String, Object> toAuditValues(TaxRate taxRate) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("uuid", taxRate.uuid());
        values.put("taxName", taxRate.taxName());
        values.put("gstRate", taxRate.gstRate());
        values.put("cgstRate", taxRate.cgstRate());
        values.put("sgstRate", taxRate.sgstRate());
        values.put("igstRate", taxRate.igstRate());
        values.put("effectiveFrom", taxRate.effectiveFrom());
        values.put("effectiveTo", taxRate.effectiveTo());
        values.put("active", taxRate.active());
        values.put("changeReason", taxRate.changeReason());
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
            computePeriodStatus(taxRate),
            taxRate.changeReason(),
            taxRate.createdAt(),
            taxRate.updatedAt()
        );
    }
}
