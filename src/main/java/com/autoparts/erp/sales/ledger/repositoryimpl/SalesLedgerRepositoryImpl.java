package com.autoparts.erp.sales.ledger.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.ledger.dto.SalesLedgerEntryResponse;
import com.autoparts.erp.sales.ledger.dto.SalesLedgerSummaryResponse;
import com.autoparts.erp.sales.ledger.repository.SalesLedgerRepository;

@Repository
public class SalesLedgerRepositoryImpl implements SalesLedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalesLedgerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * The customer is resolved by UUID in the service, then this repository
     * uses customers.id (BIGINT) through sales.customer_id. All ledger rows
     * are calculated before date/search filtering so running balances remain
     * accurate even when the user searches or views a later page.
     */
    private static final String LEDGER_CTE = """
        WITH ledger_entries AS (

            -- Completed sales increase the amount receivable from the customer.
            SELECT
                s.id AS sale_id,
                s.sale_date AS transaction_date,
                1 AS entry_order,
                0::BIGINT AS entry_id,
                'SALE_INVOICE'::VARCHAR AS transaction_type,
                s.invoice_number,
                s.customer_name,
                'Sales invoice'::VARCHAR AS description,
                s.grand_total AS debit,
                0::NUMERIC(14,2) AS credit
            FROM sales s
            WHERE s.status = 'COMPLETED'
              AND s.customer_id = ?

            UNION ALL

            -- Payments received reduce the customer's outstanding balance.
            SELECT
                s.id AS sale_id,
                sp.payment_date::DATE AS transaction_date,
                2 AS entry_order,
                sp.id AS entry_id,
                'PAYMENT_RECEIVED'::VARCHAR AS transaction_type,
                s.invoice_number,
                s.customer_name,
                ('Payment received - ' || sp.payment_method)::VARCHAR AS description,
                0::NUMERIC(14,2) AS debit,
                sp.amount AS credit
            FROM sales_payments sp
            INNER JOIN sales s ON s.id = sp.sale_id
            WHERE s.status = 'COMPLETED'
              AND s.customer_id = ?

            UNION ALL

            -- An accepted return reduces the amount owed by the customer.
            SELECT
                s.id AS sale_id,
                sr.return_date::DATE AS transaction_date,
                3 AS entry_order,
                sr.id AS entry_id,
                'SALES_RETURN'::VARCHAR AS transaction_type,
                s.invoice_number,
                s.customer_name,
                'Sales return'::VARCHAR AS description,
                0::NUMERIC(14,2) AS debit,
                sr.total_return_amount AS credit
            FROM sales_returns sr
            INNER JOIN sales s ON s.id = sr.sale_id
            WHERE s.status = 'COMPLETED'
              AND s.customer_id = ?

            UNION ALL

            -- A refund paid to the customer settles the credit created by a return.
            SELECT
                s.id AS sale_id,
                sf.refund_date::DATE AS transaction_date,
                4 AS entry_order,
                sf.id AS entry_id,
                'SALES_REFUND'::VARCHAR AS transaction_type,
                s.invoice_number,
                s.customer_name,
                ('Sales refund - ' || sf.payment_method)::VARCHAR AS description,
                sf.amount AS debit,
                0::NUMERIC(14,2) AS credit
            FROM sales_refunds sf
            INNER JOIN sales_returns sr ON sr.id = sf.sales_return_id
            INNER JOIN sales s ON s.id = sr.sale_id
            WHERE s.status = 'COMPLETED'
              AND s.customer_id = ?
        ),

        balanced_entries AS (
            SELECT
                le.*,
                SUM(le.debit - le.credit) OVER (
                    ORDER BY
                        le.transaction_date,
                        le.sale_id,
                        le.entry_order,
                        le.entry_id
                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                ) AS running_balance
            FROM ledger_entries le
        ),

        filtered_entries AS (
            SELECT *
            FROM balanced_entries
            WHERE transaction_date >= ?
              AND transaction_date <= ?
              AND (
                    CAST(? AS VARCHAR) IS NULL
                    OR invoice_number ILIKE CAST(? AS VARCHAR)
                    OR COALESCE(customer_name, '') ILIKE CAST(? AS VARCHAR)
                    OR description ILIKE CAST(? AS VARCHAR)
              )
        )
        """;

    private static final RowMapper<SalesLedgerEntryResponse> ENTRY_ROW_MAPPER =
        (rs, rowNum) -> mapEntry(rs);

    private static SalesLedgerEntryResponse mapEntry(ResultSet rs)
        throws SQLException {

        return new SalesLedgerEntryResponse(
            rs.getObject("transaction_date", LocalDate.class),
            rs.getString("transaction_type"),
            rs.getString("invoice_number"),
            rs.getString("customer_name"),
            rs.getString("description"),
            rs.getBigDecimal("debit"),
            rs.getBigDecimal("credit"),
            rs.getBigDecimal("running_balance")
        );
    }

    private Object[] ledgerAndPeriodArgs(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        return new Object[] {
            customerId, customerId, customerId, customerId,
            fromDate, toDate
        };
    }

    @Override
    public SalesLedgerSummaryResponse getSummary(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        String sql = LEDGER_CTE + """
            , opening AS (
                SELECT COALESCE(SUM(debit - credit), 0) AS opening_balance
                FROM ledger_entries
                WHERE transaction_date < ?
            ),
            period_totals AS (
                SELECT
                    COALESCE(SUM(debit), 0) AS total_debit,
                    COALESCE(SUM(credit), 0) AS total_credit
                FROM ledger_entries
                WHERE transaction_date >= ?
                  AND transaction_date <= ?
            )
            SELECT
                o.opening_balance,
                p.total_debit,
                p.total_credit,
                o.opening_balance + p.total_debit - p.total_credit AS closing_balance
            FROM opening o
            CROSS JOIN period_totals p
            """;

        Object[] base = ledgerAndPeriodArgs(customerId, fromDate, toDate);
        Object[] args = new Object[] {
            base[0], base[1], base[2], base[3], base[4], base[5],
            null, null, null, null,
            fromDate, fromDate, toDate
        };

        return jdbcTemplate.queryForObject(
            sql,
            (rs, rowNum) -> new SalesLedgerSummaryResponse(
                rs.getBigDecimal("opening_balance"),
                rs.getBigDecimal("total_debit"),
                rs.getBigDecimal("total_credit"),
                rs.getBigDecimal("closing_balance")
            ),
            args
        );
    }

    @Override
    public List<SalesLedgerEntryResponse> findEntries(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int limit,
        int offset
    ) {
        String sql = LEDGER_CTE + """
            SELECT
                transaction_date,
                transaction_type,
                invoice_number,
                customer_name,
                description,
                debit,
                credit,
                running_balance
            FROM filtered_entries
            ORDER BY transaction_date, sale_id, entry_order, entry_id
            LIMIT ? OFFSET ?
            """;

        String pattern = search == null ? null : "%" + search.trim() + "%";
        Object[] base = ledgerAndPeriodArgs(customerId, fromDate, toDate);
        Object[] args = new Object[] {
            base[0], base[1], base[2], base[3], base[4], base[5],
            pattern, pattern, pattern, pattern, limit, offset
        };

        return jdbcTemplate.query(sql, ENTRY_ROW_MAPPER, args);
    }

    @Override
    public long countEntries(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate,
        String search
    ) {
        String sql = LEDGER_CTE + " SELECT COUNT(*) FROM filtered_entries";
        String pattern = search == null ? null : "%" + search.trim() + "%";
        Object[] base = ledgerAndPeriodArgs(customerId, fromDate, toDate);
        Object[] args = new Object[] {
            base[0], base[1], base[2], base[3], base[4], base[5],
            pattern, pattern, pattern, pattern
        };

        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }
}
