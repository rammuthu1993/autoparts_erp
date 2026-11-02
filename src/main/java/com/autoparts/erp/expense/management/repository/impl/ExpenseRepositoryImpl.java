package com.autoparts.erp.expense.management.repository.impl;

import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.model.ExpenseStatus;
import com.autoparts.erp.expense.management.repository.ExpenseRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExpenseRepositoryImpl implements ExpenseRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Expense> rowMapper = (rs, rowNum) ->
        new Expense(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("category_id"),
            rs.getString("category_name"),
            rs.getDate("expense_date").toLocalDate(),
            rs.getString("title"),
            rs.getString("description"),
            rs.getBigDecimal("amount"),
            ExpensePaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getString("reference_number"),
            rs.getString("notes"),
            ExpenseStatus.valueOf(rs.getString("status")),
            rs.getLong("created_by"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class),
            rs.getObject("cancelled_at", java.time.OffsetDateTime.class)
        );

    private static final String SELECT_EXPENSE = """
        SELECT
            e.id,
            e.uuid,
            e.category_id,
            ec.name AS category_name,
            e.expense_date,
            e.title,
            e.description,
            e.amount,
            e.payment_method,
            e.reference_number,
            e.notes,
            e.status,
            e.created_by,
            e.created_at,
            e.updated_at,
            e.cancelled_at
        FROM expenses e
        INNER JOIN expense_categories ec
            ON ec.id = e.category_id
        """;

    @Override
    public Expense create(Expense expense) {

        String sql = """
            INSERT INTO expenses (
                uuid,
                category_id,
                expense_date,
                title,
                description,
                amount,
                payment_method,
                reference_number,
                notes,
                created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        UUID uuid = expense.uuid();

        Long id = jdbcTemplate.queryForObject(
            sql,
            Long.class,
            uuid,
            expense.categoryId(),
            expense.expenseDate(),
            expense.title(),
            expense.description(),
            expense.amount(),
            expense.paymentMethod().name(),
            expense.referenceNumber(),
            expense.notes(),
            expense.createdBy()
        );

        return findByUuid(uuid)
            .orElseThrow(() ->
                new IllegalStateException("Created expense could not be retrieved")
            );
    }

    @Override
    public Optional<Expense> findByUuid(UUID uuid) {

        String sql = SELECT_EXPENSE + """
            WHERE e.uuid = ?
            """;

        List<Expense> results = jdbcTemplate.query(sql, rowMapper, uuid);

        return results.stream().findFirst();
    }

    @Override
    public List<Expense> findAll() {

        String sql = SELECT_EXPENSE + """
            ORDER BY e.expense_date DESC, e.created_at DESC
            """;

        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public Optional<Expense> update(Expense expense) {

        String sql = """
            UPDATE expenses
            SET
                category_id = ?,
                expense_date = ?,
                title = ?,
                description = ?,
                amount = ?,
                payment_method = ?,
                reference_number = ?,
                notes = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
              AND status = 'ACTIVE'
            """;

        int updated = jdbcTemplate.update(
            sql,
            expense.categoryId(),
            expense.expenseDate(),
            expense.title(),
            expense.description(),
            expense.amount(),
            expense.paymentMethod().name(),
            expense.referenceNumber(),
            expense.notes(),
            expense.uuid()
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return findByUuid(expense.uuid());
    }

    @Override
    public boolean cancel(UUID uuid) {

        String sql = """
            UPDATE expenses
            SET
                status = 'CANCELLED',
                cancelled_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
              AND status = 'ACTIVE'
            """;

        return jdbcTemplate.update(sql, uuid) > 0;
    }
}