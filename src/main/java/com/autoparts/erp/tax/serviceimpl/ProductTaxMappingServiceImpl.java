package com.autoparts.erp.tax.serviceimpl;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignItemResult;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignRequest;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingCloseRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingSupersedeRequest;
import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.model.TaxErrorCode;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.autoparts.erp.tax.repository.TaxRateRepository;
import com.autoparts.erp.tax.service.ProductTaxMappingService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductTaxMappingServiceImpl implements ProductTaxMappingService {

    private final ProductTaxMappingRepository mappingRepository;
    private final TaxRateRepository taxRateRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate requiresNewTxTemplate;

    public ProductTaxMappingServiceImpl(
        ProductTaxMappingRepository mappingRepository,
        TaxRateRepository taxRateRepository,
        AuditLogService auditLogService,
        PlatformTransactionManager transactionManager
    ) {
        this.mappingRepository = mappingRepository;
        this.taxRateRepository = taxRateRepository;
        this.auditLogService = auditLogService;
        this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    public ProductTaxMappingResponse create(
        UUID productUuid,
        ProductTaxMappingCreateRequest request
    ) {
        if (request.taxRateUuid() == null) {
            throw new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND);
        }

        LocalDate effectiveFrom = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
        validateEffectiveDates(effectiveFrom, request.effectiveTo());

        Long productId = mappingRepository
            .lockActiveProduct(productUuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_NOT_FOUND));

        TaxRate taxRate = taxRateRepository.findByUuid(request.taxRateUuid())
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));

        validateTaxRatePeriod(taxRate, effectiveFrom, request.effectiveTo());

        if (mappingRepository.existsOverlappingMapping(productId, effectiveFrom, request.effectiveTo(), null)) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
        }

        try {
            ProductTaxMapping mapping = mappingRepository.create(
                UUID.randomUUID(),
                productId,
                taxRate.id(),
                request.hsnCode(),
                request.hsnDescription(),
                effectiveFrom,
                request.effectiveTo(),
                request.changeReason(),
                currentUsername()
            );

            auditLogService.log(
                "CREATE",
                "PRODUCT_TAX_MAPPING",
                mapping.uuid().toString(),
                "Created tax mapping for product: " + mapping.productCode(),
                null,
                toAuditValues(mapping, productUuid)
            );

            return toResponse(mapping, productUuid);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
        }
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public ProductTaxMappingResponse findByUuid(UUID uuid) {
        ProductTaxMapping mapping = mappingRepository.findByUuid(uuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND));

        return toResponse(mapping, findProductUuid(mapping.productId()));
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public List<ProductTaxMappingResponse> findByProductUuid(UUID productUuid) {
        return mappingRepository.findByProductUuid(productUuid)
            .stream()
            .map(mapping -> toResponse(mapping, productUuid))
            .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public PageResponse<ProductTaxMappingResponse> findPagedByProductUuid(
        UUID productUuid,
        int page,
        int size,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
        }

        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 20 : Math.min(size, 100);
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<ProductTaxMappingResponse> content = mappingRepository
            .findPagedByProductUuid(productUuid, offset, safeSize, fromDate, toDate)
            .stream()
            .map(mapping -> toResponse(mapping, productUuid))
            .toList();

        long total = mappingRepository.countPagedByProductUuid(productUuid, fromDate, toDate);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    public ProductTaxMappingResponse findCurrentByProductUuid(UUID productUuid) {
        ProductTaxMapping mapping = mappingRepository.findCurrentByProductUuid(productUuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND));

        return toResponse(mapping, productUuid);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    public ProductTaxMappingResponse closePeriod(
        UUID productUuid,
        UUID mappingUuid,
        ProductTaxMappingCloseRequest request
    ) {
        Long productId = mappingRepository.lockActiveProduct(productUuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_NOT_FOUND));

        ProductTaxMapping existing = mappingRepository.findByUuid(mappingUuid)
            .filter(m -> m.productId().equals(productId))
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND));

        if (!request.effectiveTo().isAfter(existing.effectiveFrom())) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
        }
        if (existing.effectiveTo() != null && !request.effectiveTo().isBefore(existing.effectiveTo())) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
        }

        Map<String, Object> oldValues = toAuditValues(existing, productUuid);
        ProductTaxMapping closed = mappingRepository.closePeriod(
            mappingUuid,
            request.effectiveTo(),
            request.changeReason()
        );

        auditLogService.log(
            "UPDATE",
            "PRODUCT_TAX_MAPPING",
            closed.uuid().toString(),
            "Closed mapping period for product " + closed.productCode() + " at " + request.effectiveTo()
                + " (" + request.changeReason().trim() + ")",
            oldValues,
            toAuditValues(closed, productUuid)
        );

        return toResponse(closed, productUuid);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    public ProductTaxMappingResponse supersede(
        UUID productUuid,
        UUID mappingUuid,
        ProductTaxMappingSupersedeRequest request
    ) {
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());

        Long productId = mappingRepository.lockActiveProduct(productUuid)
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_NOT_FOUND));

        ProductTaxMapping existing = mappingRepository.findByUuid(mappingUuid)
            .filter(m -> m.productId().equals(productId))
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND));

        if (!request.effectiveFrom().isAfter(existing.effectiveFrom())) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
        }
        if (existing.effectiveTo() != null && request.effectiveFrom().isAfter(existing.effectiveTo())) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
        }

        TaxRate replacementRate = taxRateRepository.findByUuid(request.taxRateUuid())
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));

        validateTaxRatePeriod(replacementRate, request.effectiveFrom(), request.effectiveTo());

        Map<String, Object> oldExistingValues = toAuditValues(existing, productUuid);

        // Close old mapping at the exact exclusive boundary of the new mapping's effectiveFrom.
        ProductTaxMapping closed = mappingRepository.closePeriod(
            mappingUuid,
            request.effectiveFrom(),
            request.changeReason()
        );

        if (mappingRepository.existsOverlappingMapping(
            productId,
            request.effectiveFrom(),
            request.effectiveTo(),
            mappingUuid
        )) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
        }

        String hsnCode = (request.hsnCode() != null && !request.hsnCode().isBlank())
            ? request.hsnCode()
            : existing.hsnCode();
        String hsnDescription = (request.hsnDescription() != null && !request.hsnDescription().isBlank())
            ? request.hsnDescription()
            : existing.hsnDescription();

        try {
            ProductTaxMapping replacement = mappingRepository.create(
                UUID.randomUUID(),
                productId,
                replacementRate.id(),
                hsnCode,
                hsnDescription,
                request.effectiveFrom(),
                request.effectiveTo(),
                request.changeReason(),
                currentUsername()
            );

            auditLogService.log(
                "UPDATE",
                "PRODUCT_TAX_MAPPING",
                closed.uuid().toString(),
                "Superseded mapping for product " + closed.productCode() + " effective " + request.effectiveFrom()
                    + " (" + request.changeReason().trim() + ")",
                oldExistingValues,
                toAuditValues(closed, productUuid)
            );
            auditLogService.log(
                "CREATE",
                "PRODUCT_TAX_MAPPING",
                replacement.uuid().toString(),
                "Created replacement mapping for product " + replacement.productCode()
                    + " superseding " + closed.uuid(),
                null,
                toAuditValues(replacement, productUuid)
            );

            return toResponse(replacement, productUuid);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    public BulkProductTaxAssignResponse bulkAssign(BulkProductTaxAssignRequest request) {
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());

        TaxRate taxRate = taxRateRepository.findByUuid(request.taxRateUuid())
            .orElseThrow(() -> new ApiException(TaxErrorCode.TAX_RATE_NOT_FOUND));

        validateTaxRatePeriod(taxRate, request.effectiveFrom(), request.effectiveTo());

        List<UUID> distinctProducts = request.productUuids().stream().distinct().toList();
        List<BulkProductTaxAssignItemResult> results = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (UUID productUuid : distinctProducts) {
            BulkProductTaxAssignItemResult itemResult = assignSingleInNewTransaction(productUuid, taxRate, request);
            results.add(itemResult);
            if (itemResult.success()) {
                succeeded++;
            } else {
                failed++;
            }
        }

        Map<String, Object> summaryAudit = new LinkedHashMap<>();
        summaryAudit.put("taxRateUuid", taxRate.uuid());
        summaryAudit.put("taxName", taxRate.taxName());
        summaryAudit.put("effectiveFrom", request.effectiveFrom());
        summaryAudit.put("effectiveTo", request.effectiveTo());
        summaryAudit.put("supersedeExisting", request.supersedeExisting());
        summaryAudit.put("totalRequested", distinctProducts.size());
        summaryAudit.put("succeeded", succeeded);
        summaryAudit.put("failed", failed);

        auditLogService.log(
            "CREATE",
            "PRODUCT_TAX_MAPPING_BULK",
            taxRate.uuid().toString(),
            "Bulk assigned GST rate " + taxRate.taxName() + " to " + succeeded + "/" + distinctProducts.size() + " products",
            null,
            summaryAudit
        );

        return new BulkProductTaxAssignResponse(distinctProducts.size(), succeeded, failed, results);
    }

    private BulkProductTaxAssignItemResult assignSingleInNewTransaction(
        UUID productUuid,
        TaxRate taxRate,
        BulkProductTaxAssignRequest request
    ) {
        try {
            return requiresNewTxTemplate.execute(status -> {
                Long productId = mappingRepository.lockActiveProduct(productUuid)
                    .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_NOT_FOUND));

                Optional<ProductTaxMapping> activeOnStart = mappingRepository.findEffectiveOnDate(productId, request.effectiveFrom());

                if (activeOnStart.isPresent()) {
                    ProductTaxMapping existing = activeOnStart.get();
                    if (!request.supersedeExisting()) {
                        throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
                    }
                    if (!request.effectiveFrom().isAfter(existing.effectiveFrom())) {
                        throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
                    }

                    String reason = (request.changeReason() != null && !request.changeReason().isBlank())
                        ? request.changeReason().trim()
                        : "Bulk tax reassignment";

                    Map<String, Object> oldValues = toAuditValues(existing, productUuid);
                    ProductTaxMapping closed = mappingRepository.closePeriod(existing.uuid(), request.effectiveFrom(), reason);

                    if (mappingRepository.existsOverlappingMapping(
                        productId, request.effectiveFrom(), request.effectiveTo(), existing.uuid())) {
                        throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
                    }

                    String hsnCode = (request.hsnCode() != null && !request.hsnCode().isBlank())
                        ? request.hsnCode()
                        : existing.hsnCode();
                    String hsnDesc = (request.hsnDescription() != null && !request.hsnDescription().isBlank())
                        ? request.hsnDescription()
                        : existing.hsnDescription();

                    ProductTaxMapping created = mappingRepository.create(
                        UUID.randomUUID(), productId, taxRate.id(), hsnCode, hsnDesc,
                        request.effectiveFrom(), request.effectiveTo(), reason, currentUsername()
                    );

                    auditLogService.log("UPDATE", "PRODUCT_TAX_MAPPING", closed.uuid().toString(),
                        "Bulk superseded mapping for " + closed.productCode(), oldValues, toAuditValues(closed, productUuid));
                    auditLogService.log("CREATE", "PRODUCT_TAX_MAPPING", created.uuid().toString(),
                        "Bulk created replacement mapping for " + created.productCode(), null, toAuditValues(created, productUuid));

                    return new BulkProductTaxAssignItemResult(
                        productUuid, created.productCode(), created.productName(),
                        true, "SUPERSEDED", "Superseded existing mapping effective " + request.effectiveFrom(), created.uuid()
                    );
                }

                if (mappingRepository.existsOverlappingMapping(productId, request.effectiveFrom(), request.effectiveTo(), null)) {
                    throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS);
                }

                ProductTaxMapping created = mappingRepository.create(
                    UUID.randomUUID(), productId, taxRate.id(), request.hsnCode(), request.hsnDescription(),
                    request.effectiveFrom(), request.effectiveTo(), request.changeReason(), currentUsername()
                );

                auditLogService.log("CREATE", "PRODUCT_TAX_MAPPING", created.uuid().toString(),
                    "Bulk created tax mapping for " + created.productCode(), null, toAuditValues(created, productUuid));

                return new BulkProductTaxAssignItemResult(
                    productUuid, created.productCode(), created.productName(),
                    true, "CREATED", "Assigned " + taxRate.taxName(), created.uuid()
                );
            });
        } catch (ApiException ex) {
            return new BulkProductTaxAssignItemResult(
                productUuid, null, null, false, "FAILED", ex.getMessage(), null
            );
        } catch (DuplicateKeyException ex) {
            return new BulkProductTaxAssignItemResult(
                productUuid, null, null, false, "FAILED", "Overlapping tax mapping exists", null
            );
        } catch (Exception ex) {
            return new BulkProductTaxAssignItemResult(
                productUuid, null, null, false, "FAILED", "Unable to assign tax mapping", null
            );
        }
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new ApiException(TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE);
        }
    }

    private void validateTaxRatePeriod(TaxRate taxRate, LocalDate mappingFrom, LocalDate mappingTo) {
        if (!taxRate.active() || mappingFrom.isBefore(taxRate.effectiveFrom())) {
            throw new ApiException(TaxErrorCode.TAX_RATE_NOT_VALID_FOR_DATE);
        }

        LocalDate taxRateTo = taxRate.effectiveTo();
        if (taxRateTo != null && (mappingTo == null || mappingTo.isAfter(taxRateTo))) {
            throw new ApiException(TaxErrorCode.TAX_RATE_NOT_VALID_FOR_DATE);
        }
    }

    private UUID findProductUuid(Long productId) {
        return mappingRepository.findProductUuidById(productId)
            .orElseThrow(() -> new ApiException(TaxErrorCode.PRODUCT_NOT_FOUND));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null && !auth.getName().isBlank()) ? auth.getName() : "system";
    }

    private String computePeriodStatus(ProductTaxMapping mapping) {
        LocalDate today = LocalDate.now();
        if (mapping.effectiveFrom().isAfter(today)) {
            return "UPCOMING";
        }
        if (mapping.effectiveTo() != null && !mapping.effectiveTo().isAfter(today)) {
            return "EXPIRED";
        }
        return "CURRENT";
    }

    private Map<String, Object> toAuditValues(ProductTaxMapping mapping, UUID productUuid) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("uuid", mapping.uuid());
        values.put("productUuid", productUuid);
        values.put("productCode", mapping.productCode());
        values.put("productName", mapping.productName());
        values.put("taxRateUuid", mapping.taxRateUuid());
        values.put("taxName", mapping.taxName());
        values.put("gstRate", mapping.gstRate());
        values.put("hsnCode", mapping.hsnCode());
        values.put("hsnDescription", mapping.hsnDescription());
        values.put("effectiveFrom", mapping.effectiveFrom());
        values.put("effectiveTo", mapping.effectiveTo());
        values.put("changeReason", mapping.changeReason());
        return values;
    }

    private ProductTaxMappingResponse toResponse(ProductTaxMapping mapping, UUID productUuid) {
        return new ProductTaxMappingResponse(
            mapping.uuid(),
            productUuid,
            mapping.productCode(),
            mapping.productName(),
            mapping.taxRateUuid(),
            mapping.taxName(),
            mapping.gstRate(),
            mapping.cgstRate(),
            mapping.sgstRate(),
            mapping.igstRate(),
            mapping.hsnCode(),
            mapping.hsnDescription(),
            mapping.effectiveFrom(),
            mapping.effectiveTo(),
            computePeriodStatus(mapping),
            mapping.changeReason(),
            mapping.createdBy(),
            mapping.createdAt(),
            mapping.updatedAt()
        );
    }
}
