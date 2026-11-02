package com.autoparts.erp.expense.management.repository;

import com.autoparts.erp.expense.management.model.Expense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository {

    Expense create(Expense expense);

    Optional<Expense> findByUuid(UUID uuid);

    List<Expense> findAll();

    Optional<Expense> update(Expense expense);

    boolean cancel(UUID uuid);
}