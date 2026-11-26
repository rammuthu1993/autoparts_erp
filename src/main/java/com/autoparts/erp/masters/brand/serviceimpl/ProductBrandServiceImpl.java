
package com.autoparts.erp.masters.brand.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.brand.model.BrandErrorCode;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repository.ProductBrandRepository;

import com.autoparts.erp.masters.brand.service.ProductBrandService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductBrandServiceImpl implements ProductBrandService {

    private final ProductBrandRepository repository;
    private final AuditLogService auditLogService;

    public ProductBrandServiceImpl(
        ProductBrandRepository repository,
        AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("BRANDS.CREATE")
    public BrandResponse create(BrandCreateRequest request) {

        String name = request.name().trim();

        ProductBrand brand = new ProductBrand(
            null,
            UUID.randomUUID(),
            name,
            request.description(),
            true,
            null,
            null
        );

        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(
                BrandErrorCode.BRAND_ALREADY_EXISTS
            );
        }

        try {
            ProductBrand savedBrand = repository.create(brand);

            auditLogService.log(
                "CREATE",
                "PRODUCT_BRAND",
                savedBrand.uuid().toString(),
                "Product brand created: " + savedBrand.name(),
                null,
                toAuditValues(savedBrand)
            );

            return toResponse(savedBrand);

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A brand with this name already exists"
            );
        }
    }

    @Override
    @SalesAccess
    @RequiresPermission("BRANDS.VIEW")
    public List<BrandResponse> findAll() {

        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("BRANDS.VIEW")
    public PageResponse<BrandResponse> findPaged(
            int page,
            int size,
            String search,
            String sortBy,
            String sortDir
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<BrandResponse> content = repository.findPaged(
                offset,
                safeSize,
                search,
                sortBy,
                sortDir
        ).stream().map(this::toResponse).toList();

        long total = repository.countPaged(search);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @SalesAccess
    @RequiresPermission("BRANDS.VIEW")
    public BrandResponse findByUuid(UUID uuid) {

        ProductBrand brand = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Brand not found"
            ));

        return toResponse(brand);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("BRANDS.UPDATE")
    public BrandResponse update(
        UUID uuid,
        BrandUpdateRequest request
    ) {

        ProductBrand existing = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Brand not found"
            ));

        try {
            boolean updated = repository.update(
                uuid,
                request.name().trim(),
                request.description()
            );

            if (!updated) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Active brand not found"
                );
            }

            ProductBrand updatedBrand = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Brand not found"
                ));

            auditLogService.log(
                "UPDATE",
                "PRODUCT_BRAND",
                uuid.toString(),
                "Product brand updated: " + updatedBrand.name(),
                toAuditValues(existing),
                toAuditValues(updatedBrand)
            );

            return toResponse(updatedBrand);

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A brand with this name already exists"
            );
        }
    }

    @Override
    @Transactional
    @AdminOnly
    @RequiresPermission("BRANDS.DEACTIVATE")
    public void deactivate(UUID uuid) {

        ProductBrand existing = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active brand not found"
            ));

        boolean deactivated = repository.deactivate(uuid);

        if (!deactivated) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active brand not found"
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("name", existing.name());
        newValues.put("description", existing.description());
        newValues.put("active", false);

        auditLogService.log(
            "DELETE",
            "PRODUCT_BRAND",
            uuid.toString(),
            "Product brand deactivated: " + existing.name(),
            toAuditValues(existing),
            newValues
        );
    }

    private BrandResponse toResponse(ProductBrand brand) {

        return new BrandResponse(
            brand.id(),
            brand.uuid(),
            brand.name(),
            brand.description(),
            brand.active(),
            brand.createdAt(),
            brand.updatedAt()
        );
    }

    private Map<String, Object> toAuditValues(ProductBrand brand) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("name", brand.name());
        values.put("description", brand.description());
        values.put("active", brand.active());

        return values;
    }
}
