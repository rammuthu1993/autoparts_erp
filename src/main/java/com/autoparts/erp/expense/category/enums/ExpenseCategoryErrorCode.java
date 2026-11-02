package com.autoparts.erp.expense.category.enums;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum ExpenseCategoryErrorCode implements ApiErrorCodeDefinition {

    EXPENSE_CATEGORY_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Expense category not found"
    ),

    EXPENSE_CATEGORY_NAME_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "An expense category with this name already exists"
    ),

    INVALID_EXPENSE_CATEGORY_DATA(
        HttpStatus.BAD_REQUEST,
        "Invalid expense category data"
    );

    private final HttpStatus status;
    private final String message;

    ExpenseCategoryErrorCode(HttpStatus status, String message) {
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