package com.autoparts.erp.masters.brand.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.brand.dto.BrandUsageResponse;
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
import java.util.Locale;
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
        String name = normalizeName(request.name());
        String code = normalizeCode(request.code());
        String description = normalizeText(request.description());
        String countryOfOrigin = normalizeText(request.countryOfOrigin());
        String website = normalizeText(request.website());

        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(BrandErrorCode.BRAND_ALREADY_EXISTS);
        }
        if (code != null && repository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A brand with this code already exists"
            );
        }

        ProductBrand brand = new ProductBrand(
                null,
                UUID.randomUUID(),
                code,
                name,
                description,
                countryOfOrigin,
                website,
                0L,
                0L,
                true,
                null,
                null
        );

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
                    "A brand with this name or code already exists"
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
    public List<BrandResponse> findAllActive() {
        return repository.findAllActive()
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
            String status,
            String sortBy,
            String sortDir
    ) {
        String normalizedStatus = normalizeStatusFilter(status);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<BrandResponse> content = repository.findPaged(
                offset,
                safeSize,
                search,
                normalizedStatus,
                sortBy,
                sortDir
        ).stream().map(this::toResponse).toList();

        long total = repository.countPaged(search, normalizedStatus);
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
    @SalesAccess
    @RequiresPermission("BRANDS.VIEW")
    public BrandUsageResponse getUsage(UUID uuid) {
        BrandUsageResponse usage = repository.getUsage(uuid);
        if (usage == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Brand not found"
            );
        }
        return usage;
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

        if (!existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inactive brand must be reactivated before editing"
            );
        }

        String name = normalizeName(request.name());
        String code = normalizeCode(request.code());
        String description = normalizeText(request.description());
        String countryOfOrigin = normalizeText(request.countryOfOrigin());
        String website = normalizeText(request.website());

        repository.findByNameIgnoreCase(name)
                .filter(other -> !other.uuid().equals(uuid))
                .ifPresent(other -> {
                    throw new ApiException(BrandErrorCode.BRAND_ALREADY_EXISTS);
                });

        if (code != null) {
            repository.findByCodeIgnoreCase(code)
                    .filter(other -> !other.uuid().equals(uuid))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "A brand with this code already exists"
                        );
                    });
        }

        try {
            boolean updated = repository.update(
                    uuid,
                    code,
                    name,
                    description,
                    countryOfOrigin,
                    website
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
                    "A brand with this name or code already exists"
            );
        }
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("BRANDS.DEACTIVATE")
    public void deactivate(UUID uuid) {
        ProductBrand existing = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Brand not found"
                ));

        if (!existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Brand is already inactive"
            );
        }

        boolean deactivated = repository.deactivate(uuid);
        if (!deactivated) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Brand could not be deactivated"
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>(toAuditValues(existing));
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

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("BRANDS.DEACTIVATE")
    public BrandResponse reactivate(UUID uuid) {
        ProductBrand existing = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Brand not found"
                ));

        if (existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Brand is already active"
            );
        }

        boolean reactivated = repository.reactivate(uuid);
        if (!reactivated) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Brand could not be reactivated"
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
                "Product brand reactivated: " + updatedBrand.name(),
                toAuditValues(existing),
                toAuditValues(updatedBrand)
        );

        return toResponse(updatedBrand);
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    private String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "ALL";
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!upper.equals("ALL") && !upper.equals("ACTIVE") && !upper.equals("INACTIVE")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status filter. Allowed values: ALL, ACTIVE, INACTIVE"
            );
        }
        return upper;
    }

    private BrandResponse toResponse(ProductBrand brand) {
        return new BrandResponse(
                brand.id(),
                brand.uuid(),
                brand.code(),
                brand.name(),
                brand.description(),
                brand.countryOfOrigin(),
                brand.website(),
                brand.productCount(),
                brand.activeProductCount(),
                brand.active(),
                brand.createdAt(),
                brand.updatedAt()
        );
    }

    private Map<String, Object> toAuditValues(ProductBrand brand) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", brand.code());
        values.put("name", brand.name());
        values.put("description", brand.description());
        values.put("countryOfOrigin", brand.countryOfOrigin());
        values.put("website", brand.website());
        values.put("active", brand.active());
        return values;
    }
}
