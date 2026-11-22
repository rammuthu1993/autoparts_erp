package com.autoparts.erp.expense.management.repositoryimpl;

import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.model.ExpenseStatus;
import com.autoparts.erp.expense.management.repository.ExpenseRepository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExpenseRepositoryImpl implements ExpenseRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExpenseRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
            VALUES (
                :uuid,
                :categoryId,
                :expenseDate,
                :title,
                :description,
                :amount,
                :paymentMethod,
                :referenceNumber,
                :notes,
                :createdBy
            )
            RETURNING id
            """;

        UUID uuid = expense.uuid();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("categoryId", expense.categoryId())
                .addValue("expenseDate", expense.expenseDate())
                .addValue("title", expense.title())
                .addValue("description", expense.description())
                .addValue("amount", expense.amount())
                .addValue("paymentMethod", expense.paymentMethod().name())
                .addValue("referenceNumber", expense.referenceNumber())
                .addValue("notes", expense.notes())
                .addValue("createdBy", expense.createdBy());

        Long id = jdbcTemplate.queryForObject(
            sql,
            params,
            Long.class
        );

        return findByUuid(uuid)
            .orElseThrow(() ->
                new IllegalStateException("Created expense could not be retrieved")
            );
    }

    @Override
    public Optional<Expense> findByUuid(UUID uuid) {

        String sql = SELECT_EXPENSE + """
            WHERE e.uuid = :uuid
            """;

        List<Expense> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("uuid", uuid),
                rowMapper
        );

        return results.stream().findFirst();
    }

    private static final Map<String, String> EXPENSE_SORT_ALLOWLIST = Map.of(
            "id", "e.id",
            "expenseDate", "e.expense_date",
            "title", "e.title",
            "amount", "e.amount",
            "status", "e.status",
            "paymentMethod", "e.payment_method",
            "categoryName", "ec.name",
            "createdAt", "e.created_at"
    );

    @Override
    public List<Expense> findAll() {

        String sql = SELECT_EXPENSE + """
            ORDER BY e.expense_date DESC, e.created_at DESC, e.id DESC
            LIMIT 501
            """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public List<Expense> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String paymentMethod,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(SELECT_EXPENSE).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyExpenseFilters(sql, params, search, status, paymentMethod);

        String sortColumn = EXPENSE_SORT_ALLOWLIST.getOrDefault(sortBy, "e.expense_date");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"e.id".equals(sortColumn)) {
            sql.append(", e.id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public long countPaged(String search, String status, String paymentMethod) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM expenses e
            JOIN expense_categories ec ON ec.id = e.category_id
            WHERE 1=1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyExpenseFilters(sql, params, search, status, paymentMethod);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyExpenseFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String status,
            String paymentMethod
    ) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND (e.title ILIKE :search OR e.description ILIKE :search OR ec.name ILIKE :search OR e.reference_number ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            sql.append(" AND e.payment_method = :paymentMethod");
            params.addValue("paymentMethod", paymentMethod.trim().toUpperCase());
        }
    }

    @Override
    public Optional<Expense> update(Expense expense) {

        String sql = """
            UPDATE expenses
            SET
                category_id = :categoryId,
                expense_date = :expenseDate,
                title = :title,
                description = :description,
                amount = :amount,
                payment_method = :paymentMethod,
                reference_number = :referenceNumber,
                notes = :notes,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
              AND status = 'ACTIVE'
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("categoryId", expense.categoryId())
                .addValue("expenseDate", expense.expenseDate())
                .addValue("title", expense.title())
                .addValue("description", expense.description())
                .addValue("amount", expense.amount())
                .addValue("paymentMethod", expense.paymentMethod().name())
                .addValue("referenceNumber", expense.referenceNumber())
                .addValue("notes", expense.notes())
                .addValue("uuid", expense.uuid());

        int updated = jdbcTemplate.update(sql, params);

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
            WHERE uuid = :uuid
              AND status = 'ACTIVE'
            """;

        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }
}
