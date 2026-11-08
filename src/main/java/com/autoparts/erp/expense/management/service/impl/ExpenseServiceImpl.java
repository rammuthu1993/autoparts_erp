
package com.autoparts.erp.expense.management.service.impl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repository.ExpenseCategoryRepository;
import com.autoparts.erp.expense.management.dto.CreateExpenseRequest;
import com.autoparts.erp.expense.management.dto.UpdateExpenseRequest;
import com.autoparts.erp.expense.management.enums.ExpenseErrorCode;
import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.model.ExpenseStatus;
import com.autoparts.erp.expense.management.repository.ExpenseRepository;
import com.autoparts.erp.expense.management.service.ExpenseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {

    private static final Logger log =
        LoggerFactory.getLogger(ExpenseServiceImpl.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;
    private final CashRegisterService cashRegisterService;
    private final AuditLogService auditLogService;

    public ExpenseServiceImpl(
        ExpenseRepository expenseRepository,
        ExpenseCategoryRepository categoryRepository,
        CurrentUserService currentUserService,
        CashRegisterService cashRegisterService,
        AuditLogService auditLogService
    ) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserService = currentUserService;
        this.cashRegisterService = cashRegisterService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
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
            ExpenseStatus.ACTIVE,
            currentUserId,
            null,
            null,
            null
        );

        Expense created = expenseRepository.create(expense);

        // Record cash outflow only for CASH expenses.
        if (created.paymentMethod() == ExpensePaymentMethod.CASH) {
            cashRegisterService.recordTransactionMovement(
                CashMovementType.OUT,
                CashMovementSourceType.EXPENSE,
                created.uuid(),
                created.amount(),
                "Cash expense: " + created.title()
            );
        }

        auditLogService.log(
            "CREATE",
            "EXPENSE",
            created.uuid().toString(),
            "Expense created: " + created.title(),
            null,
            toAuditValues(created)
        );

        log.info(
            "Expense created successfully. Expense UUID: {}",
            created.uuid()
        );

        return created;
    }

    @Override
    @SalesAccess
    public Expense findByUuid(UUID uuid) {

        return expenseRepository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(ExpenseErrorCode.EXPENSE_NOT_FOUND)
            );
    }

    @Override
    @SalesAccess
    public List<Expense> findAll() {
        return expenseRepository.findAll();
    }

    @Override
    @Transactional
    @AdminOrManager
    public Expense update(UUID uuid, UpdateExpenseRequest request) {

        Expense existing = findByUuid(uuid);

        if (existing.status() != ExpenseStatus.ACTIVE) {
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

        // Adjust cash register after successfully updating the expense.
        adjustCashMovement(existing, updated);

        auditLogService.log(
            "UPDATE",
            "EXPENSE",
            updated.uuid().toString(),
            "Expense updated: " + updated.title(),
            toAuditValues(existing),
            toAuditValues(updated)
        );

        log.info(
            "Expense updated successfully. Expense UUID: {}",
            uuid
        );

        return updated;
    }

    @Override
    @Transactional
    @AdminOrManager
    public void cancel(UUID uuid) {

        Expense existing = findByUuid(uuid);

        if (existing.status() != ExpenseStatus.ACTIVE) {
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

        // Reverse the original cash outflow when cancelling a cash expense.
        if (existing.paymentMethod() == ExpensePaymentMethod.CASH) {
            recordCashAdjustment(
                CashMovementType.IN,
                existing.amount(),
                existing.uuid(),
                "Cash expense cancelled and reversed"
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", "CANCELLED");
        newValues.put("amount", existing.amount());
        newValues.put("paymentMethod", existing.paymentMethod().name());

        auditLogService.log(
            "CANCEL",
            "EXPENSE",
            existing.uuid().toString(),
            "Expense cancelled: " + existing.title(),
            toAuditValues(existing),
            newValues
        );

        log.info(
            "Expense cancelled successfully. Expense UUID: {}",
            uuid
        );
    }

    /**
     * Compares the old and updated expense and records
     * only the required cash difference.
     */
    private void adjustCashMovement(
        Expense existing,
        Expense updated
    ) {

        boolean wasCash =
            existing.paymentMethod() == ExpensePaymentMethod.CASH;

        boolean isCash =
            updated.paymentMethod() == ExpensePaymentMethod.CASH;

        // CASH -> CASH
        if (wasCash && isCash) {

            int comparison =
                updated.amount().compareTo(existing.amount());

            if (comparison > 0) {

                BigDecimal difference =
                    updated.amount().subtract(existing.amount());

                recordCashAdjustment(
                    CashMovementType.OUT,
                    difference,
                    existing.uuid(),
                    "Additional cash expense amount"
                );

            } else if (comparison < 0) {

                BigDecimal difference =
                    existing.amount().subtract(updated.amount());

                recordCashAdjustment(
                    CashMovementType.IN,
                    difference,
                    existing.uuid(),
                    "Cash expense amount reduced"
                );
            }

            return;
        }

        // CASH -> UPI / CARD / BANK
        if (wasCash) {

            recordCashAdjustment(
                CashMovementType.IN,
                existing.amount(),
                existing.uuid(),
                "Cash expense reversed due to payment method change"
            );

            return;
        }

        // UPI / CARD / BANK -> CASH
        if (isCash) {

            recordCashAdjustment(
                CashMovementType.OUT,
                updated.amount(),
                existing.uuid(),
                "Expense changed to CASH payment"
            );
        }
    }

    /**
     * Records an immutable compensating movement.
     *
     * A new UUID is used for every adjustment because the
     * cash movement table enforces unique source_type/source_uuid.
     */
    private void recordCashAdjustment(
        CashMovementType movementType,
        BigDecimal amount,
        UUID expenseUuid,
        String description
    ) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        cashRegisterService.recordTransactionMovement(
            movementType,
            CashMovementSourceType.ADJUSTMENT,
            UUID.randomUUID(),
            amount,
            description + " - Expense UUID: " + expenseUuid
        );
    }

    private ExpenseCategory getActiveCategory(UUID categoryUuid) {

        ExpenseCategory category =
            categoryRepository.findByUuid(categoryUuid)
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

    private Map<String, Object> toAuditValues(Expense expense) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("categoryId", expense.categoryId());
        values.put("categoryName", expense.categoryName());
        values.put("expenseDate", expense.expenseDate());
        values.put("title", expense.title());
        values.put("amount", expense.amount());
        values.put("paymentMethod", expense.paymentMethod().name());
        values.put("status", expense.status().name());

        return values;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
