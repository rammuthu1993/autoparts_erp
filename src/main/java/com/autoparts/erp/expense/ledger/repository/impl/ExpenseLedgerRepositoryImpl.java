package com.autoparts.erp.expense.ledger.repository.impl;

import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerEntry;
import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerSummary;
import com.autoparts.erp.expense.ledger.repository.ExpenseLedgerRepository;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class ExpenseLedgerRepositoryImpl
    implements ExpenseLedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseLedgerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String FILTER_SQL = """
        FROM expenses e
        INNER JOIN expense_categories ec
            ON ec.id = e.category_id
        WHERE e.status = 'ACTIVE'
          AND (CAST(? AS DATE) IS NULL OR e.expense_date >= CAST(? AS DATE))
          AND (CAST(? AS DATE) IS NULL OR e.expense_date <= CAST(? AS DATE))
          AND (CAST(? AS UUID) IS NULL OR ec.uuid = CAST(? AS UUID))
          AND (
              CAST(? AS VARCHAR) IS NULL
              OR e.payment_method = CAST(? AS VARCHAR)
          )
          AND (
              CAST(? AS VARCHAR) IS NULL
              OR e.title ILIKE '%' || CAST(? AS VARCHAR) || '%'
              OR COALESCE(e.description, '') ILIKE '%' || CAST(? AS VARCHAR) || '%'
              OR COALESCE(e.reference_number, '') ILIKE '%' || CAST(? AS VARCHAR) || '%'
              OR ec.name ILIKE '%' || CAST(? AS VARCHAR) || '%'
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

        private Object[] filterParams(
        	    LocalDate fromDate,
        	    LocalDate toDate,
        	    UUID categoryUuid,
        	    ExpensePaymentMethod paymentMethod,
        	    String search
        	) {
        	    String method = paymentMethod == null
        	        ? null
        	        : paymentMethod.name();

        	    String searchValue = search == null || search.isBlank()
        	        ? null
        	        : search.trim();

        	    return new Object[] {
        	        fromDate, fromDate,
        	        toDate, toDate,
        	        categoryUuid, categoryUuid,
        	        method, method,
        	        searchValue,
        	        searchValue,
        	        searchValue,
        	        searchValue,
        	        searchValue
        	    };
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
            LIMIT ? OFFSET ?
            """;

        Object[] filters = filterParams(
            fromDate, toDate, categoryUuid, paymentMethod, search
        );

        Object[] params = new Object[filters.length + 2];

        System.arraycopy(filters, 0, params, 0, filters.length);

        params[filters.length] = limit;
        params[filters.length + 1] = offset;

        return jdbcTemplate.query(sql, entryRowMapper, params);
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

        Object[] params = filterParams(
            fromDate, toDate, categoryUuid, paymentMethod, search
        );

        return jdbcTemplate.queryForObject(
            sql,
            (rs, rowNum) -> new ExpenseLedgerSummary(
                rs.getLong("total_expenses"),
                rs.getBigDecimal("total_amount")
            ),
            params
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

        Object[] params = filterParams(
            fromDate, toDate, categoryUuid, paymentMethod, search
        );

        Long count = jdbcTemplate.queryForObject(
            sql,
            Long.class,
            params
        );

        return count == null ? 0 : count;
    }
}