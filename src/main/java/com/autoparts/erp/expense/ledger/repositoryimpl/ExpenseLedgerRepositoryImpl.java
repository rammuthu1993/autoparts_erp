package com.autoparts.erp.expense.ledger.repositoryimpl;

import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerEntry;
import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerSummary;
import com.autoparts.erp.expense.ledger.repository.ExpenseLedgerRepository;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class ExpenseLedgerRepositoryImpl
    implements ExpenseLedgerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExpenseLedgerRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String FILTER_SQL = """
        FROM expenses e
        INNER JOIN expense_categories ec
            ON ec.id = e.category_id
        WHERE e.status = 'ACTIVE'
          AND (CAST(:fromDate AS DATE) IS NULL OR e.expense_date >= CAST(:fromDate AS DATE))
          AND (CAST(:toDate AS DATE) IS NULL OR e.expense_date <= CAST(:toDate AS DATE))
          AND (CAST(:categoryUuid AS UUID) IS NULL OR ec.uuid = CAST(:categoryUuid AS UUID))
          AND (
              CAST(:paymentMethod AS VARCHAR) IS NULL
              OR e.payment_method = CAST(:paymentMethod AS VARCHAR)
          )
          AND (
              CAST(:searchPattern AS VARCHAR) IS NULL
              OR e.title ILIKE :searchPattern
              OR COALESCE(e.description, '') ILIKE :searchPattern
              OR COALESCE(e.reference_number, '') ILIKE :searchPattern
              OR ec.name ILIKE :searchPattern
          )
        """;

    private final RowMapper<ExpenseLedgerEntry> entryRowMapper =
        (rs, rowNum) -> new ExpenseLedgerEntry(
            rs.getObject("expense_uuid", UUID.class),
            rs.getDate("expense_date").toLocalDate(),
            rs.getString("title"),
            rs.getString("category_name"),
            rs.getBigDecimal("amount"),
            ExpensePaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getString("reference_number"),
            rs.getString("status")
        );

    private MapSqlParameterSource filterParams(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryUuid,
            ExpensePaymentMethod paymentMethod,
            String search
    ) {
        String method = paymentMethod == null
            ? null
            : paymentMethod.name();

        String searchPattern = (search == null || search.isBlank())
            ? null
            : "%" + search.trim() + "%";

        return new MapSqlParameterSource()
            .addValue("fromDate", fromDate)
            .addValue("toDate", toDate)
            .addValue("categoryUuid", categoryUuid)
            .addValue("paymentMethod", method)
            .addValue("searchPattern", searchPattern);
    }

    @Override
    public List<ExpenseLedgerEntry> findEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search,
        int limit,
        long offset
    ) {

        String sql = """
            SELECT
                e.uuid AS expense_uuid,
                e.expense_date,
                e.title,
                ec.name AS category_name,
                e.amount,
                e.payment_method,
                e.reference_number,
                e.status
            """ + FILTER_SQL + """
            ORDER BY e.expense_date DESC, e.created_at DESC, e.id DESC
            LIMIT :limit OFFSET :offset
            """;

        MapSqlParameterSource params = filterParams(
            fromDate, toDate, categoryUuid, paymentMethod, search
        )
        .addValue("limit", limit)
        .addValue("offset", offset);

        return jdbcTemplate.query(sql, params, entryRowMapper);
    }

    @Override
    public ExpenseLedgerSummary getSummary(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search
    ) {

        String sql = """
            SELECT
                COUNT(*) AS total_expenses,
                COALESCE(SUM(e.amount), 0) AS total_amount
            """ + FILTER_SQL;

        MapSqlParameterSource params = filterParams(
            fromDate, toDate, categoryUuid, paymentMethod, search
        );

        return jdbcTemplate.queryForObject(
            sql,
            params,
            (rs, rowNum) -> new ExpenseLedgerSummary(
                rs.getLong("total_expenses"),
                rs.getBigDecimal("total_amount")
            )
        );
    }

    @Override
    public long countEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryUuid,
        ExpensePaymentMethod paymentMethod,
        String search
    ) {

        String sql = "SELECT COUNT(*) " + FILTER_SQL;

        MapSqlParameterSource params = filterParams(
            fromDate, toDate, categoryUuid, paymentMethod, search
        );

        Long count = jdbcTemplate.queryForObject(
            sql,
            params,
            Long.class
        );

        return count == null ? 0 : count;
    }
}
