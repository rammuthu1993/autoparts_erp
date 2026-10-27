package com.autoparts.erp.masters.category.service;

import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryResponse;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repository.ProductCategoryRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true) // Default all methods to read-only for performance
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository repository;

    public ProductCategoryServiceImpl(ProductCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional // Enables write transaction
    public CategoryResponse create(CategoryCreateRequest request) {
        String description = request.description() != null ? request.description().trim() : null;

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
            return toResponse(savedCategory);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name already exists"
            );
        }
    }

    @Override
    public List<CategoryResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
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
    public CategoryResponse update(UUID uuid, CategoryUpdateRequest request) {
        String description = request.description() != null ? request.description().trim() : null;

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

            return findByUuid(uuid);

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name already exists"
            );
        }
    }

    @Override
    @Transactional
    public void deactivate(UUID uuid) {
        boolean deactivated = repository.deactivate(uuid);

        if (!deactivated) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Active category not found"
            );
        }
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
}