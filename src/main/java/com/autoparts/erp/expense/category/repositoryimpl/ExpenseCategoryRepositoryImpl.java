package com.autoparts.erp.expense.category.repositoryimpl;

import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repository.ExpenseCategoryRepository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExpenseCategoryRepositoryImpl
        implements ExpenseCategoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExpenseCategoryRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
            VALUES (:uuid, :name, :description, :active)
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", category.uuid())
                .addValue("name", category.name())
                .addValue("description", category.description())
                .addValue("active", category.active());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            rowMapper
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

        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public Optional<ExpenseCategory> findByUuid(UUID uuid) {

        String sql = """
            SELECT *
            FROM expense_categories
            WHERE uuid = :uuid
            """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("uuid", uuid),
                rowMapper
            )
            .stream()
            .findFirst();
    }

    @Override
    public boolean update(UUID uuid, ExpenseCategory category) {

        String sql = """
            UPDATE expense_categories
            SET name = :name,
                description = :description,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
              AND active = TRUE
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", category.name())
                .addValue("description", category.description())
                .addValue("uuid", uuid);

        return jdbcTemplate.update(sql, params) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {

        String sql = """
            UPDATE expense_categories
            SET active = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
              AND active = TRUE
            """;

        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public boolean existsByName(String name) {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM expense_categories
                WHERE LOWER(name) = LOWER(:name)
            )
            """;

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource("name", name),
            Boolean.class
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
                WHERE LOWER(name) = LOWER(:name)
                  AND uuid <> :uuid
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("uuid", uuid);

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            params,
            Boolean.class
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
