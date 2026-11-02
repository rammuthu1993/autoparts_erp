package com.autoparts.erp.expense.category.service;

import com.autoparts.erp.expense.category.dto.ExpenseCategoryCreateRequest;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryResponse;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface ExpenseCategoryService {

    ExpenseCategoryResponse create(ExpenseCategoryCreateRequest request);

    List<ExpenseCategoryResponse> findAll();

    ExpenseCategoryResponse findByUuid(UUID uuid);

    ExpenseCategoryResponse update(
        UUID uuid,
        ExpenseCategoryUpdateRequest request
    );

    void deactivate(UUID uuid);
}