package com.autoparts.erp.expense.category.repository.impl;

import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repository.ExpenseCategoryRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExpenseCategoryRepositoryImpl
        implements ExpenseCategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseCategoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ExpenseCategory> rowMapper =
        this::mapRow;

    @Override
    public ExpenseCategory create(ExpenseCategory category) {

        String sql = """
            INSERT INTO expense_categories (
                uuid,
                name,
                description,
                active
            )
            VALUES (?, ?, ?, ?)
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            rowMapper,
            category.uuid(),
            category.name(),
            category.description(),
            category.active()
        );
    }

    @Override
    public List<ExpenseCategory> findAll() {

        String sql = """
            SELECT *
            FROM expense_categories
            WHERE active = TRUE
            ORDER BY name ASC
            """;

        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public Optional<ExpenseCategory> findByUuid(UUID uuid) {

        String sql = """
            SELECT *
            FROM expense_categories
            WHERE uuid = ?
            """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                uuid
            )
            .stream()
            .findFirst();
    }

    @Override
    public boolean update(UUID uuid, ExpenseCategory category) {

        String sql = """
            UPDATE expense_categories
            SET name = ?,
                description = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
              AND active = TRUE
            """;

        return jdbcTemplate.update(
            sql,
            category.name(),
            category.description(),
            uuid
        ) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {

        String sql = """
            UPDATE expense_categories
            SET active = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
              AND active = TRUE
            """;

        return jdbcTemplate.update(sql, uuid) > 0;
    }

    @Override
    public boolean existsByName(String name) {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM expense_categories
                WHERE LOWER(name) = LOWER(?)
            )
            """;

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            name
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByNameExcludingUuid(
            String name,
            UUID uuid
    ) {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM expense_categories
                WHERE LOWER(name) = LOWER(?)
                  AND uuid <> ?
            )
            """;

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            name,
            uuid
        );

        return Boolean.TRUE.equals(exists);
    }

    private ExpenseCategory mapRow(
            ResultSet rs,
            int rowNum
    ) throws SQLException {

        return new ExpenseCategory(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("active"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}