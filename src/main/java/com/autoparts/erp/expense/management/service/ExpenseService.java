package com.autoparts.erp.expense.management.service;

import com.autoparts.erp.expense.management.dto.CreateExpenseRequest;
import com.autoparts.erp.expense.management.dto.UpdateExpenseRequest;
import com.autoparts.erp.expense.management.model.Expense;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {

    Expense create(CreateExpenseRequest request);

    Expense findByUuid(UUID uuid);

    List<Expense> findAll();

    Expense update(UUID uuid, UpdateExpenseRequest request);

    void cancel(UUID uuid);
}