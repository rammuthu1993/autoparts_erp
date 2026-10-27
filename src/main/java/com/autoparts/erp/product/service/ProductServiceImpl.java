
package com.autoparts.erp.product.service;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.product.dto.ProductCreateRequest;
import com.autoparts.erp.product.dto.ProductResponse;
import com.autoparts.erp.product.dto.ProductUpdateRequest;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.model.ProductErrorCode;
import com.autoparts.erp.product.repository.ProductRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;

    public ProductServiceImpl(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {

        String name = request.name().trim();
        String partNo = request.partNo().trim();
        String unit = request.unit().trim();

        // Validate category
        if (!repository.categoryExistsAndActive(request.categoryId())) {
            throw new ApiException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        // Validate brand
        if (!repository.brandExistsAndActive(request.brandId())) {
            throw new ApiException(ProductErrorCode.BRAND_NOT_FOUND);
        }

        // Check duplicate product name
        if (repository.existsByName(name)) {
            throw new ApiException(
                    ProductErrorCode.PRODUCT_NAME_ALREADY_EXISTS
            );
        }

        // Check duplicate part number
        if (repository.existsByPartNo(partNo)) {
            throw new ApiException(
                    ProductErrorCode.PRODUCT_PART_NO_ALREADY_EXISTS
            );
        }

        // Generate a sequential business item ID
        long number = repository.nextItemNumber();
        String productId = "GAP-%06d".formatted(number);

        // Check generated item ID
        if (repository.existsByItemId(productId)) {
            throw new ApiException(
                    ProductErrorCode.PRODUCT_ITEM_ID_ALREADY_EXISTS
            );
        }

        try {
            Product product = repository.create(
                    UUID.randomUUID(),
                    productId,
                    partNo,
                    name,
                    request.description(),
                    request.categoryId(),
                    request.brandId(),
                    request.purchasePrice(),
                    request.sellingPrice(),
                    request.qty(),
                    request.minimumStock(),
                    unit
            );

            return toResponse(product);

        } catch (DuplicateKeyException ex) {
            // Protect against concurrent requests and database constraints.
            // The exact constraint can be identified in a future refinement.
            throw new ApiException(
                    ProductErrorCode.INVALID_PRODUCT_DATA
            );
        }
    }

    @Override
    public List<ProductResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProductResponse findByUuid(UUID uuid) {
        Product product = repository.findByUuid(uuid)
                .orElseThrow(() ->
                        new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND)
                );

        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse update(
            UUID uuid,
            ProductUpdateRequest request
    ) {
        // Ensure the product exists and is active.
        findByUuid(uuid);

        // Validate category and brand.
        if (!repository.categoryExistsAndActive(request.categoryId())) {
            throw new ApiException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        if (!repository.brandExistsAndActive(request.brandId())) {
            throw new ApiException(ProductErrorCode.BRAND_NOT_FOUND);
        }

        String name = request.name().trim();
        String partNo = request.partNo().trim();

        // Check duplicates, excluding the product being updated.
        if (repository.existsByName(name)) {
            throw new ApiException(
                    ProductErrorCode.PRODUCT_NAME_ALREADY_EXISTS
            );
        }

        if (repository.existsByPartNo(partNo)) {
            throw new ApiException(
                    ProductErrorCode.PRODUCT_PART_NO_ALREADY_EXISTS
            );
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

            return findByUuid(uuid);

        } catch (DuplicateKeyException ex) {
            throw new ApiException(
                    ProductErrorCode.INVALID_PRODUCT_DATA
            );
        }
    }

    @Override
    @Transactional
    public void deactivate(UUID uuid) {
        boolean deactivated = repository.deactivate(uuid);

        if (!deactivated) {
            throw new ApiException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.id(),
                product.uuid(),
                product.itemId(),
                product.partNo(),
                product.name(),
                product.description(),
                product.categoryId(),
                product.categoryName(),
                product.brandId(),
                product.brandName(),
                product.purchasePrice(),
                product.sellingPrice(),
                product.qty(),
                product.minimumStock(),
                product.unit(),
                product.active(),
                product.createdAt(),
                product.updatedAt()
        );
    }
}
