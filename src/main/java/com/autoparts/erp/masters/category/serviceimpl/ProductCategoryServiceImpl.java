
package com.autoparts.erp.masters.category.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryResponse;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repository.ProductCategoryRepository;

import com.autoparts.erp.masters.category.service.ProductCategoryService;
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
    public CategoryResponse create(CategoryCreateRequest request) {

        String description = request.description() != null
            ? request.description().trim()
            : null;

        ProductCategory category = new ProductCategory(
            null,
            UUID.randomUUID(),
            request.name().trim(),
            description,
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
                "A category with this name already exists"
            );
        }
    }

    @Override
    @SalesAccess
    public List<CategoryResponse> findAll() {

        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @SalesAccess
    public CategoryResponse findByUuid(UUID uuid) {

        ProductCategory category = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Category not found"
            ));

        return toResponse(category);
    }

    @Override
    @Transactional
    @AdminOrManager
    public CategoryResponse update(
        UUID uuid,
        CategoryUpdateRequest request
    ) {

        ProductCategory existing = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Category not found"
            ));

        String description = request.description() != null
            ? request.description().trim()
            : null;

        try {
            boolean updated = repository.update(
                uuid,
                request.name().trim(),
                description
            );

            if (!updated) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Active category not found"
                );
            }

            CategoryResponse response = findByUuid(uuid);

            Map<String, Object> newValues = new LinkedHashMap<>();
            newValues.put("name", response.name());
            newValues.put("description", response.description());
            newValues.put("active", response.active());

            auditLogService.log(
                "UPDATE",
                "PRODUCT_CATEGORY",
                uuid.toString(),
                "Product category updated: " + response.name(),
                toAuditValues(existing),
                newValues
            );

            return response;

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A category with this name already exists"
            );
        }
    }

    @Override
    @Transactional
    @AdminOnly
    public void deactivate(UUID uuid) {

        ProductCategory existing = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active category not found"
            ));

        boolean deactivated = repository.deactivate(uuid);

        if (!deactivated) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active category not found"
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("name", existing.name());
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

    private CategoryResponse toResponse(ProductCategory category) {

        return new CategoryResponse(
            category.id(),
            category.uuid(),
            category.name(),
            category.description(),
            category.active(),
            category.createdAt(),
            category.updatedAt()
        );
    }

    private Map<String, Object> toAuditValues(
        ProductCategory category
    ) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("name", category.name());
        values.put("description", category.description());
        values.put("active", category.active());

        return values;
    }
}
