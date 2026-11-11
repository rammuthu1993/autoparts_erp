package com.autoparts.erp.product.serviceimpl;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.product.dto.ProductCreateRequest;
import com.autoparts.erp.product.dto.ProductResponse;
import com.autoparts.erp.product.dto.ProductUpdateRequest;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.model.ProductErrorCode;
import com.autoparts.erp.product.repository.ProductRepository;
import com.autoparts.erp.product.service.ProductService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final String BARCODE_TYPE_CODE_128 = "CODE_128";

    private final ProductRepository repository;
    private final AuditLogService auditLogService;

    public ProductServiceImpl(
            ProductRepository repository,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
    public ProductResponse create(ProductCreateRequest request) {
        String name = request.name().trim();
        String partNo = request.partNo().trim();
        String unit = request.unit().trim();

        if (!repository.categoryExistsAndActive(request.categoryId())) {
            throw new ApiException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        if (!repository.brandExistsAndActive(request.brandId())) {
            throw new ApiException(ProductErrorCode.BRAND_NOT_FOUND);
        }

        if (repository.existsByName(name)) {
            throw new ApiException(ProductErrorCode.PRODUCT_NAME_ALREADY_EXISTS);
        }

        if (repository.existsByPartNo(partNo)) {
            throw new ApiException(ProductErrorCode.PRODUCT_PART_NO_ALREADY_EXISTS);
        }

        long number = repository.nextProductCodeNumber();
        String productCode = "GAP-%06d".formatted(number);

        if (repository.existsByProductCode(productCode)) {
            throw new ApiException(ProductErrorCode.PRODUCT_CODE_ALREADY_EXISTS);
        }

        try {
            // CODE_128 barcode value is derived from the unique productCode.
            String barcode = productCode;

            Product product = repository.create(
                    UUID.randomUUID(),
                    productCode,
                    partNo,
                    name,
                    request.description(),
                    request.categoryId(),
                    request.brandId(),
                    request.purchasePrice(),
                    request.sellingPrice(),
                    request.minimumStock(),
                    unit,
                    barcode,
                    BARCODE_TYPE_CODE_128
            );

            ProductResponse response = toResponse(product);

            auditLogService.log(
                    "CREATE",
                    "PRODUCT",
                    response.productCode(),
                    "Product created",
                    null,
                    toAuditValues(response)
            );

            return response;

        } catch (DuplicateKeyException ex) {
            throw new ApiException(ProductErrorCode.INVALID_PRODUCT_DATA);
        }
    }

    @Override
    @SalesAccess
    public List<ProductResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    public ProductResponse findByUuid(UUID uuid) {
        Product product = repository.findByUuid(uuid)
                .orElseThrow(() ->
                        new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return toResponse(product);
    }

    @Override
    @Transactional
    @AdminOrManager
    public ProductResponse update(UUID uuid, ProductUpdateRequest request) {
        Product existingProduct = repository.findByUuid(uuid)
                .orElseThrow(() ->
                        new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND));

        ProductResponse oldResponse = toResponse(existingProduct);

        if (!repository.categoryExistsAndActive(request.categoryId())) {
            throw new ApiException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        if (!repository.brandExistsAndActive(request.brandId())) {
            throw new ApiException(ProductErrorCode.BRAND_NOT_FOUND);
        }

        String name = request.name().trim();
        String partNo = request.partNo().trim();

        if (repository.existsByNameExcludingUuid(name, uuid)) {
            throw new ApiException(ProductErrorCode.PRODUCT_NAME_ALREADY_EXISTS);
        }

        if (repository.existsByPartNoExcludingUuid(partNo, uuid)) {
            throw new ApiException(ProductErrorCode.PRODUCT_PART_NO_ALREADY_EXISTS);
        }

        try {
            boolean updated = repository.update(
                    uuid,
                    partNo,
                    name,
                    request.description(),
                    request.categoryId(),
                    request.brandId(),
                    request.purchasePrice(),
                    request.sellingPrice(),
                    request.minimumStock(),
                    request.unit().trim()
            );

            if (!updated) {
                throw new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND);
            }

            ProductResponse updatedResponse = findByUuid(uuid);

            auditLogService.log(
                    "UPDATE",
                    "PRODUCT",
                    updatedResponse.productCode(),
                    "Product updated",
                    toAuditValues(oldResponse),
                    toAuditValues(updatedResponse)
            );

            return updatedResponse;

        } catch (DuplicateKeyException ex) {
            throw new ApiException(ProductErrorCode.INVALID_PRODUCT_DATA);
        }
    }

    @Override
    @Transactional
    @AdminOnly
    public void deactivate(UUID uuid) {
        Product existingProduct = repository.findByUuid(uuid)
                .orElseThrow(() ->
                        new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND));

        ProductResponse existingResponse = toResponse(existingProduct);

        boolean deactivated = repository.deactivate(uuid);

        if (!deactivated) {
            throw new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        auditLogService.log(
                "DELETE",
                "PRODUCT",
                existingResponse.productCode(),
                "Product deactivated",
                toAuditValues(existingResponse),
                Map.of("active", false)
        );
    }

    private Map<String, Object> toAuditValues(ProductResponse product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productCode", product.productCode());
        values.put("partNo", product.partNo());
        values.put("name", product.name());
        values.put("description", product.description());
        values.put("categoryId", product.categoryId());
        values.put("brandId", product.brandId());
        values.put("purchasePrice", product.purchasePrice());
        values.put("sellingPrice", product.sellingPrice());
        values.put("minimumStock", product.minimumStock());
        values.put("unit", product.unit());
        values.put("barcode", product.barcode());
        values.put("barcodeType", product.barcodeType());
        values.put("active", product.active());
        return values;
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.id(),
                product.uuid(),
                product.productCode(),
                product.partNo(),
                product.name(),
                product.description(),
                product.categoryId(),
                product.categoryName(),
                product.brandId(),
                product.brandName(),
                product.purchasePrice(),
                product.sellingPrice(),
                product.minimumStock(),
                product.unit(),
                product.barcode(),
                product.barcodeType(),
                product.active(),
                product.createdAt(),
                product.updatedAt()
        );
    }
}
