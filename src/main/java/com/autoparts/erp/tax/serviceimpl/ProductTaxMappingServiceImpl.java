
package com.autoparts.erp.tax.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;
import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.model.TaxErrorCode;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.autoparts.erp.tax.repository.TaxRateRepository;

import com.autoparts.erp.tax.service.ProductTaxMappingService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductTaxMappingServiceImpl
        implements ProductTaxMappingService {

    private final ProductTaxMappingRepository mappingRepository;
    private final TaxRateRepository taxRateRepository;
    private final AuditLogService auditLogService;

    public ProductTaxMappingServiceImpl(
        ProductTaxMappingRepository mappingRepository,
        TaxRateRepository taxRateRepository,
        AuditLogService auditLogService
    ) {
        this.mappingRepository = mappingRepository;
        this.taxRateRepository = taxRateRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
    public ProductTaxMappingResponse create(
        UUID productUuid,
        ProductTaxMappingCreateRequest request
    ) {

        validateEffectiveDates(
            request.effectiveFrom(),
            request.effectiveTo()
        );

        // Lock product row to serialize mapping changes for this product.
        Long productId = mappingRepository
            .lockActiveProduct(productUuid)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.PRODUCT_NOT_FOUND
            ));

        TaxRate taxRate = taxRateRepository
            .findByUuid(request.taxRateUuid())
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.TAX_RATE_NOT_FOUND
            ));

        validateTaxRatePeriod(
            taxRate,
            request.effectiveFrom(),
            request.effectiveTo()
        );

        if (mappingRepository.existsOverlappingMapping(
            productId,
            request.effectiveFrom(),
            request.effectiveTo()
        )) {
            throw new ApiException(
                TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS
            );
        }

        try {

            ProductTaxMapping mapping =
                mappingRepository.create(
                    UUID.randomUUID(),
                    productId,
                    taxRate.id(),
                    request.hsnCode(),
                    request.effectiveFrom(),
                    request.effectiveTo()
                );

            // Audit successful product tax mapping creation.
            auditLogService.log(
                "CREATE",
                "PRODUCT_TAX_MAPPING",
                mapping.uuid().toString(),
                "Created tax mapping for product: "
                    + mapping.productCode(),
                null,
                toAuditValues(mapping, productUuid)
            );

            return toResponse(mapping, productUuid);

        } catch (DuplicateKeyException exception) {

            // The database remains the final protection against
            // concurrent duplicate mapping creation.
            throw new ApiException(
                TaxErrorCode.PRODUCT_TAX_MAPPING_ALREADY_EXISTS
            );
        }
    }

    @Override
    @SalesAccess
    public ProductTaxMappingResponse findByUuid(UUID uuid) {

        ProductTaxMapping mapping = mappingRepository
            .findByUuid(uuid)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND
            ));

        // Retrieve the external product UUID without exposing
        // the internal numeric product ID.
        return toResponse(
            mapping,
            findProductUuid(mapping.productId())
        );
    }

    @Override
    @SalesAccess
    public List<ProductTaxMappingResponse> findByProductUuid(
        UUID productUuid
    ) {

        return mappingRepository
            .findByProductUuid(productUuid)
            .stream()
            .map(mapping -> toResponse(mapping, productUuid))
            .toList();
    }

    @Override
    @SalesAccess
    public ProductTaxMappingResponse findCurrentByProductUuid(
        UUID productUuid
    ) {

        ProductTaxMapping mapping = mappingRepository
            .findCurrentByProductUuid(productUuid)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND
            ));

        return toResponse(mapping, productUuid);
    }

    private void validateEffectiveDates(
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {

        if (effectiveTo != null
            && !effectiveTo.isAfter(effectiveFrom)) {

            throw new ApiException(
                TaxErrorCode.PRODUCT_TAX_MAPPING_INVALID_DATE
            );
        }
    }

    private void validateTaxRatePeriod(
        TaxRate taxRate,
        LocalDate mappingFrom,
        LocalDate mappingTo
    ) {

        if (!taxRate.active()
            || mappingFrom.isBefore(taxRate.effectiveFrom())) {

            throw new ApiException(
                TaxErrorCode.TAX_RATE_NOT_VALID_FOR_DATE
            );
        }

        LocalDate taxRateTo = taxRate.effectiveTo();

        if (taxRateTo != null
            && (mappingTo == null || mappingTo.isAfter(taxRateTo))) {

            throw new ApiException(
                TaxErrorCode.TAX_RATE_NOT_VALID_FOR_DATE
            );
        }
    }

    private UUID findProductUuid(Long productId) {

        // Retrieve the external product UUID.
        return mappingRepository.findProductUuidById(productId)
            .orElseThrow(() -> new ApiException(
                TaxErrorCode.PRODUCT_NOT_FOUND
            ));
    }

    private Map<String, Object> toAuditValues(
        ProductTaxMapping mapping,
        UUID productUuid
    ) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("productUuid", productUuid);
        values.put("productCode", mapping.productCode());
        values.put("productName", mapping.productName());
        values.put("taxRateUuid", mapping.taxRateUuid());
        values.put("taxName", mapping.taxName());
        values.put("hsnCode", mapping.hsnCode());
        values.put("effectiveFrom", mapping.effectiveFrom());
        values.put("effectiveTo", mapping.effectiveTo());

        return values;
    }

    private ProductTaxMappingResponse toResponse(
        ProductTaxMapping mapping,
        UUID productUuid
    ) {

        return new ProductTaxMappingResponse(
            mapping.uuid(),
            productUuid,
            mapping.productCode(),
            mapping.productName(),
            mapping.taxRateUuid(),
            mapping.taxName(),
            mapping.hsnCode(),
            mapping.effectiveFrom(),
            mapping.effectiveTo(),
            mapping.createdAt()
        );
    }
}
