package com.autoparts.erp.expense.management.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.expense.management.dto.CreateExpenseRequest;
import com.autoparts.erp.expense.management.dto.UpdateExpenseRequest;
import com.autoparts.erp.expense.management.model.Expense;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {

    Expense create(CreateExpenseRequest request);

    Expense findByUuid(UUID uuid);

    List<Expense> findAll();

    PageResponse<Expense> findPaged(
        int page,
        int size,
        String search,
        String status,
        String paymentMethod,
        String sortBy,
        String sortDir
    );

    Expense update(UUID uuid, UpdateExpenseRequest request);

    void cancel(UUID uuid);
}