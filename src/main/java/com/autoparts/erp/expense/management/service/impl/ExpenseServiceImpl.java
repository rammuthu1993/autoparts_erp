package com.autoparts.erp.expense.management.service.impl;

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repository.ExpenseCategoryRepository;
import com.autoparts.erp.expense.management.dto.CreateExpenseRequest;
import com.autoparts.erp.expense.management.dto.UpdateExpenseRequest;
import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.enums.ExpenseErrorCode;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.repository.ExpenseRepository;
import com.autoparts.erp.expense.management.service.ExpenseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {

    private static final Logger log =
        LoggerFactory.getLogger(ExpenseServiceImpl.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    public ExpenseServiceImpl(
        ExpenseRepository expenseRepository,
        ExpenseCategoryRepository categoryRepository,
        CurrentUserService currentUserService
    ) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public Expense create(CreateExpenseRequest request) {

        ExpenseCategory category =
            getActiveCategory(request.categoryUuid());

        Long currentUserId = currentUserService.getCurrentUserId();

        Expense expense = new Expense(
            null,
            UUID.randomUUID(),
            category.id(),
            category.name(),
            request.expenseDate(),
            request.title().trim(),
            clean(request.description()),
            request.amount(),
            request.paymentMethod(),
            clean(request.referenceNumber()),
            clean(request.notes()),
            com.autoparts.erp.expense.management.model.ExpenseStatus.ACTIVE,
            currentUserId,
            null,
            null,
            null
        );

        Expense created = expenseRepository.create(expense);

        log.info("Expense created successfully. Expense UUID: {}",
            created.uuid());

        return created;
    }

    @Override
    public Expense findByUuid(UUID uuid) {

        return expenseRepository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(ExpenseErrorCode.EXPENSE_NOT_FOUND)
            );
    }

    @Override
    public List<Expense> findAll() {
        return expenseRepository.findAll();
    }

    @Override
    @Transactional
    public Expense update(UUID uuid, UpdateExpenseRequest request) {

        Expense existing = findByUuid(uuid);

        if (existing.status() !=
            com.autoparts.erp.expense.management.model.ExpenseStatus.ACTIVE) {

            throw new ApiException(
                ExpenseErrorCode.EXPENSE_UPDATE_NOT_ALLOWED
            );
        }

        ExpenseCategory category =
            getActiveCategory(request.categoryUuid());

        Expense updatedExpense = new Expense(
            existing.id(),
            existing.uuid(),
            category.id(),
            category.name(),
            request.expenseDate(),
            request.title().trim(),
            clean(request.description()),
            request.amount(),
            request.paymentMethod(),
            clean(request.referenceNumber()),
            clean(request.notes()),
            existing.status(),
            existing.createdBy(),
            existing.createdAt(),
            existing.updatedAt(),
            existing.cancelledAt()
        );

        Expense updated = expenseRepository.update(updatedExpense)
            .orElseThrow(() ->
                new ApiException(
                    ExpenseErrorCode.EXPENSE_UPDATE_NOT_ALLOWED
                )
            );

        log.info("Expense updated successfully. Expense UUID: {}", uuid);

        return updated;
    }

    @Override
    @Transactional
    public void cancel(UUID uuid) {

        Expense existing = findByUuid(uuid);

        if (existing.status() !=
            com.autoparts.erp.expense.management.model.ExpenseStatus.ACTIVE) {

            throw new ApiException(
                ExpenseErrorCode.EXPENSE_ALREADY_CANCELLED
            );
        }

        boolean cancelled = expenseRepository.cancel(uuid);

        if (!cancelled) {
            throw new ApiException(
                ExpenseErrorCode.EXPENSE_ALREADY_CANCELLED
            );
        }

        log.info("Expense cancelled successfully. Expense UUID: {}", uuid);
    }

    private ExpenseCategory getActiveCategory(UUID categoryUuid) {

        ExpenseCategory category = categoryRepository.findByUuid(categoryUuid)
            .orElseThrow(() ->
                new ApiException(
                    ExpenseErrorCode.EXPENSE_CATEGORY_NOT_FOUND
                )
            );

        if (!category.active()) {
            throw new ApiException(
                ExpenseErrorCode.EXPENSE_CATEGORY_INACTIVE
            );
        }

        return category;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}