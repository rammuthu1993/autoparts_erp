package com.autoparts.erp.expense.management.enums;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum ExpenseErrorCode implements ApiErrorCodeDefinition {

    EXPENSE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Expense not found"
    ),

    EXPENSE_CATEGORY_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Expense category not found"
    ),

    EXPENSE_CATEGORY_INACTIVE(
        HttpStatus.BAD_REQUEST,
        "Cannot create or update an expense using an inactive category"
    ),

    EXPENSE_ALREADY_CANCELLED(
        HttpStatus.CONFLICT,
        "Expense is already cancelled or cannot be cancelled"
    ),

    EXPENSE_UPDATE_NOT_ALLOWED(
        HttpStatus.CONFLICT,
        "Only active expenses can be updated"
    );

    private final HttpStatus status;
    private final String message;

    ExpenseErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}