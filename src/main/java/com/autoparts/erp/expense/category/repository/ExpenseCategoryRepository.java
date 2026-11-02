package com.autoparts.erp.expense.category.repository;

import com.autoparts.erp.expense.category.model.ExpenseCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository {

    ExpenseCategory create(ExpenseCategory category);

    List<ExpenseCategory> findAll();

    Optional<ExpenseCategory> findByUuid(UUID uuid);

    boolean update(UUID uuid, ExpenseCategory category);

    boolean deactivate(UUID uuid);

    boolean existsByName(String name);

    boolean existsByNameExcludingUuid(String name, UUID uuid);
}