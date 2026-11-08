
package com.autoparts.erp.expense.category.service.impl;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExpenseCategoryServiceImpl
    implements ExpenseCategoryService {

    private static final Logger log =
        LoggerFactory.getLogger(ExpenseCategoryServiceImpl.class);

    private final ExpenseCategoryRepository repository;
    private final AuditLogService auditLogService;

    public ExpenseCategoryServiceImpl(
        ExpenseCategoryRepository repository,
        AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOnly
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

            auditLogService.log(
                "CREATE",
                "EXPENSE_CATEGORY",
                saved.uuid().toString(),
                "Expense category created: " + saved.name(),
                null,
                toAuditValues(saved)
            );

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
    @SalesAccess
    public List<ExpenseCategoryResponse> findAll() {

        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @SalesAccess
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
    @AdminOnly
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

            auditLogService.log(
                "UPDATE",
                "EXPENSE_CATEGORY",
                uuid.toString(),
                "Expense category updated: " + updated.name(),
                toAuditValues(existing),
                toAuditValues(updated)
            );

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
    @AdminOnly
    public void deactivate(UUID uuid) {

        ExpenseCategory existing = repository.findByUuid(uuid)
            .filter(ExpenseCategory::active)
            .orElseThrow(() ->
                new ApiException(
                    ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NOT_FOUND
                )
            );

        if (!repository.deactivate(uuid)) {
            throw new ApiException(
                ExpenseCategoryErrorCode.EXPENSE_CATEGORY_NOT_FOUND
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("name", existing.name());
        newValues.put("active", false);

        auditLogService.log(
            "DELETE",
            "EXPENSE_CATEGORY",
            uuid.toString(),
            "Expense category deactivated: " + existing.name(),
            toAuditValues(existing),
            newValues
        );

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

    private Map<String, Object> toAuditValues(
        ExpenseCategory category
    ) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("name", category.name());
        values.put("description", category.description());
        values.put("active", category.active());

        return values;
    }

    private String clean(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
