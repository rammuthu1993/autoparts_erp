package com.autoparts.erp.masters.category.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryResponse;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;
import com.autoparts.erp.masters.category.dto.CategoryUsageResponse;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repository.ProductCategoryRepository;
import com.autoparts.erp.masters.category.service.ProductCategoryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository repository;
    private final AuditLogService auditLogService;

    public ProductCategoryServiceImpl(
            ProductCategoryRepository repository,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CATEGORIES.CREATE")
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = normalizeName(request.name());
        String code = normalizeCode(request.code());
        String description = normalizeText(request.description());

        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name already exists"
            );
        }
        if (code != null && repository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this code already exists"
            );
        }

        Long parentId = resolveParentId(null, request.parentUuid());

        ProductCategory category = new ProductCategory(
                null,
                UUID.randomUUID(),
                code,
                name,
                description,
                parentId,
                null,
                null,
                0L,
                0L,
                true,
                null,
                null
        );

        try {
            ProductCategory savedCategory = repository.create(category);

            auditLogService.log(
                    "CREATE",
                    "PRODUCT_CATEGORY",
                    savedCategory.uuid().toString(),
                    "Product category created: " + savedCategory.name(),
                    null,
                    toAuditValues(savedCategory)
            );

            return toResponse(savedCategory);

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name or code already exists"
            );
        }
    }

    @Override
    @SalesAccess
    @RequiresPermission("CATEGORIES.VIEW")
    public List<CategoryResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("CATEGORIES.VIEW")
    public List<CategoryResponse> findAllActive() {
        return repository.findAllActive()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("CATEGORIES.VIEW")
    public PageResponse<CategoryResponse> findPaged(
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

        List<CategoryResponse> content = repository.findPaged(
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
    @RequiresPermission("CATEGORIES.VIEW")
    public CategoryResponse findByUuid(UUID uuid) {
        ProductCategory category = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found"
                ));

        return toResponse(category);
    }

    @Override
    @SalesAccess
    @RequiresPermission("CATEGORIES.VIEW")
    public CategoryUsageResponse getUsage(UUID uuid) {
        CategoryUsageResponse usage = repository.getUsage(uuid);
        if (usage == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Category not found"
            );
        }
        return usage;
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CATEGORIES.UPDATE")
    public CategoryResponse update(
            UUID uuid,
            CategoryUpdateRequest request
    ) {
        ProductCategory existing = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found"
                ));

        if (!existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inactive category must be reactivated before editing"
            );
        }

        String name = normalizeName(request.name());
        String code = normalizeCode(request.code());
        String description = normalizeText(request.description());

        repository.findByNameIgnoreCase(name)
                .filter(other -> !other.uuid().equals(uuid))
                .ifPresent(other -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A category with this name already exists"
                    );
                });

        if (code != null) {
            repository.findByCodeIgnoreCase(code)
                    .filter(other -> !other.uuid().equals(uuid))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "A category with this code already exists"
                        );
                    });
        }

        Long parentId = resolveParentId(existing, request.parentUuid());

        try {
            boolean updated = repository.update(
                    uuid,
                    code,
                    name,
                    description,
                    parentId
            );

            if (!updated) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active category not found"
                );
            }

            ProductCategory updatedCategory = repository.findByUuid(uuid)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Category not found"
                    ));

            auditLogService.log(
                    "UPDATE",
                    "PRODUCT_CATEGORY",
                    uuid.toString(),
                    "Product category updated: " + updatedCategory.name(),
                    toAuditValues(existing),
                    toAuditValues(updatedCategory)
            );

            return toResponse(updatedCategory);

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name or code already exists"
            );
        }
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CATEGORIES.DEACTIVATE")
    public void deactivate(UUID uuid) {
        ProductCategory existing = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found"
                ));

        if (!existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category is already inactive"
            );
        }

        boolean deactivated = repository.deactivate(uuid);
        if (!deactivated) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category could not be deactivated"
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>(toAuditValues(existing));
        newValues.put("active", false);

        auditLogService.log(
                "DELETE",
                "PRODUCT_CATEGORY",
                uuid.toString(),
                "Product category deactivated: " + existing.name(),
                toAuditValues(existing),
                newValues
        );
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CATEGORIES.DEACTIVATE")
    public CategoryResponse reactivate(UUID uuid) {
        ProductCategory existing = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found"
                ));

        if (existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category is already active"
            );
        }

        boolean reactivated = repository.reactivate(uuid);
        if (!reactivated) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category could not be reactivated"
            );
        }

        ProductCategory updatedCategory = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found"
                ));

        auditLogService.log(
                "UPDATE",
                "PRODUCT_CATEGORY",
                uuid.toString(),
                "Product category reactivated: " + updatedCategory.name(),
                toAuditValues(existing),
                toAuditValues(updatedCategory)
        );

        return toResponse(updatedCategory);
    }

    private Long resolveParentId(ProductCategory current, UUID parentUuid) {
        if (parentUuid == null) {
            return null;
        }
        if (current != null && current.uuid().equals(parentUuid)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category cannot be its own parent"
            );
        }
        ProductCategory parent = repository.findByUuid(parentUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Parent category not found"
                ));
        if (!parent.active() && (current == null || !parent.id().equals(current.parentId()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Parent category must be active"
            );
        }
        if (current != null) {
            Set<Long> visited = new HashSet<>();
            visited.add(current.id());
            Long cursorId = parent.id();
            int depth = 0;
            while (cursorId != null && depth < 25) {
                if (!visited.add(cursorId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Circular parent category hierarchy is not allowed"
                    );
                }
                cursorId = repository.findById(cursorId)
                        .map(ProductCategory::parentId)
                        .orElse(null);
                depth++;
            }
        }
        return parent.id();
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

    private CategoryResponse toResponse(ProductCategory category) {
        return new CategoryResponse(
                category.id(),
                category.uuid(),
                category.code(),
                category.name(),
                category.description(),
                category.parentId(),
                category.parentUuid(),
                category.parentName(),
                category.productCount(),
                category.activeProductCount(),
                category.active(),
                category.createdAt(),
                category.updatedAt()
        );
    }

    private Map<String, Object> toAuditValues(ProductCategory category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", category.code());
        values.put("name", category.name());
        values.put("description", category.description());
        values.put("parentId", category.parentId());
        values.put("active", category.active());
        return values;
    }
}
