package com.autoparts.erp.expense.category.service.impl;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryCreateRequest;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryResponse;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryUpdateRequest;
import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.enums.ExpenseCategoryErrorCode;
import com.autoparts.erp.expense.category.repository.ExpenseCategoryRepository;
import com.autoparts.erp.expense.category.service.ExpenseCategoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExpenseCategoryServiceImpl
        implements ExpenseCategoryService {

    private static final Logger log =
        LoggerFactory.getLogger(ExpenseCategoryServiceImpl.class);

    private final ExpenseCategoryRepository repository;

    public ExpenseCategoryServiceImpl(
            ExpenseCategoryRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse create(
            ExpenseCategoryCreateRequest request
    ) {

        String name = clean(request.name());

        if (repository.existsByName(name)) {
            throw new ApiException(
                ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NAME_ALREADY_EXISTS
            );
        }

        ExpenseCategory category = new ExpenseCategory(
            null,
            UUID.randomUUID(),
            name,
            clean(request.description()),
            true,
            null,
            null
        );

        try {
            ExpenseCategory saved = repository.create(category);

            log.info(
                "Expense category created successfully. categoryUuid={}",
                saved.uuid()
            );

            return toResponse(saved);

        } catch (DuplicateKeyException exception) {

            // Protects against concurrent requests passing the pre-check.
            log.warn(
                "Duplicate expense category creation rejected. categoryName={}",
                name
            );

            throw new ApiException(
                ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NAME_ALREADY_EXISTS
            );
        }
    }

    @Override
    public List<ExpenseCategoryResponse> findAll() {

        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public ExpenseCategoryResponse findByUuid(UUID uuid) {

        ExpenseCategory category = repository.findByUuid(uuid)
            .filter(ExpenseCategory::active)
            .orElseThrow(() ->
                new ApiException(
                    ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NOT_FOUND
                )
            );

        return toResponse(category);
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse update(
            UUID uuid,
            ExpenseCategoryUpdateRequest request
    ) {

        ExpenseCategory existing = repository.findByUuid(uuid)
            .filter(ExpenseCategory::active)
            .orElseThrow(() ->
                new ApiException(
                    ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NOT_FOUND
                )
            );

        String name = clean(request.name());

        if (repository.existsByNameExcludingUuid(name, uuid)) {
            throw new ApiException(
                ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NAME_ALREADY_EXISTS
            );
        }

        ExpenseCategory updated = new ExpenseCategory(
            existing.id(),
            existing.uuid(),
            name,
            clean(request.description()),
            existing.active(),
            existing.createdAt(),
            existing.updatedAt()
        );

        try {
            if (!repository.update(uuid, updated)) {
                throw new ApiException(
                    ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NOT_FOUND
                );
            }

            log.info(
                "Expense category updated successfully. categoryUuid={}",
                uuid
            );

            return findByUuid(uuid);

        } catch (DuplicateKeyException exception) {

            log.warn(
                "Duplicate expense category update rejected. categoryUuid={}",
                uuid
            );

            throw new ApiException(
                ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NAME_ALREADY_EXISTS
            );
        }
    }

    @Override
    @Transactional
    public void deactivate(UUID uuid) {

        if (!repository.deactivate(uuid)) {
            throw new ApiException(
                ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NOT_FOUND
            );
        }

        log.info(
            "Expense category deactivated successfully. categoryUuid={}",
            uuid
        );
    }

    private ExpenseCategoryResponse toResponse(
            ExpenseCategory category
    ) {

        return new ExpenseCategoryResponse(
            category.id(),
            category.uuid(),
            category.name(),
            category.description(),
            category.active(),
            category.createdAt(),
            category.updatedAt()
        );
    }

    private String clean(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}