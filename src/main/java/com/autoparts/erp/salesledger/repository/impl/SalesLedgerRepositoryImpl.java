package com.autoparts.erp.salesledger.repository.impl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.salesledger.dto.SalesLedgerEntryResponse;
import com.autoparts.erp.salesledger.dto.SalesLedgerSummaryResponse;
import com.autoparts.erp.salesledger.repository.SalesLedgerRepository;

@Repository
public class SalesLedgerRepositoryImpl implements SalesLedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalesLedgerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String LEDGER_CTE = """
        WITH ledger_entries AS (

            SELECT
                s.id AS sale_id,
                s.sale_date AS transaction_date,
                1 AS entry_order,
                0::BIGINT AS payment_id,

                'SALE_INVOICE'::VARCHAR AS transaction_type,

                s.invoice_number,
                s.customer_name,

                'Sales invoice'::VARCHAR AS description,

                s.grand_total AS debit,
                0::NUMERIC(14,2) AS credit

            FROM sales s

            WHERE s.status = 'COMPLETED'

            UNION ALL

            SELECT
                s.id AS sale_id,
                sp.payment_date::DATE AS transaction_date,
                2 AS entry_order,
                sp.id AS payment_id,

                'PAYMENT_RECEIVED'::VARCHAR AS transaction_type,

                s.invoice_number,
                s.customer_name,

                (
                    'Payment received - ' || sp.payment_method
                )::VARCHAR AS description,

                0::NUMERIC(14,2) AS debit,
                sp.amount AS credit

            FROM sales_payments sp

            INNER JOIN sales s
                ON s.id = sp.sale_id

            WHERE s.status = 'COMPLETED'
        ),

        filtered_entries AS (

            SELECT *
            FROM ledger_entries

            WHERE transaction_date >= ?
              AND transaction_date <= ?

              AND (
			    CAST(? AS VARCHAR) IS NULL
			    OR invoice_number ILIKE CAST(? AS VARCHAR)
			    OR COALESCE(customer_name, '') ILIKE CAST(? AS VARCHAR)
			)
        ),

        opening AS (

            SELECT
                COALESCE(
                    SUM(debit - credit),
                    0
                ) AS opening_balance

            FROM ledger_entries

            WHERE transaction_date < ?

              AND (
			    CAST(? AS VARCHAR) IS NULL
			    OR invoice_number ILIKE CAST(? AS VARCHAR)
			    OR COALESCE(customer_name, '') ILIKE CAST(? AS VARCHAR)
			)
        ),

        balanced_entries AS (

            SELECT
                fe.*,

                o.opening_balance
                +
                SUM(fe.debit - fe.credit) OVER (
                    ORDER BY
                        fe.transaction_date,
                        fe.sale_id,
                        fe.entry_order,
                        fe.payment_id

                    ROWS BETWEEN UNBOUNDED PRECEDING
                    AND CURRENT ROW
                ) AS running_balance

            FROM filtered_entries fe

            CROSS JOIN opening o
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

    @Override
    public SalesLedgerSummaryResponse getSummary(
        LocalDate fromDate,
        LocalDate toDate,
        String search
    ) {

        String sql = LEDGER_CTE + """
            SELECT
                o.opening_balance,

                COALESCE(SUM(fe.debit), 0) AS total_debit,

                COALESCE(SUM(fe.credit), 0) AS total_credit,

                o.opening_balance
                    + COALESCE(SUM(fe.debit), 0)
                    - COALESCE(SUM(fe.credit), 0)
                    AS closing_balance

            FROM opening o

            LEFT JOIN filtered_entries fe
                ON TRUE

            GROUP BY o.opening_balance
            """;

        String pattern = search == null
            ? null
            : "%" + search.trim() + "%";

        return jdbcTemplate.queryForObject(
            sql,
            (rs, rowNum) -> new SalesLedgerSummaryResponse(
                rs.getBigDecimal("opening_balance"),
                rs.getBigDecimal("total_debit"),
                rs.getBigDecimal("total_credit"),
                rs.getBigDecimal("closing_balance")
            ),
            fromDate,
            toDate,
            pattern,
            pattern,
            pattern,
            fromDate,
            pattern,
            pattern,
            pattern
        );
    }

    @Override
    public List<SalesLedgerEntryResponse> findEntries(
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

            FROM balanced_entries

            ORDER BY
                transaction_date,
                sale_id,
                entry_order,
                payment_id

            LIMIT ?
            OFFSET ?
            """;

        String pattern = search == null
            ? null
            : "%" + search.trim() + "%";

        return jdbcTemplate.query(
            sql,
            ENTRY_ROW_MAPPER,
            fromDate,
            toDate,
            pattern,
            pattern,
            pattern,
            fromDate,
            pattern,
            pattern,
            pattern,
            limit,
            offset
        );
    }

    @Override
    public long countEntries(
        LocalDate fromDate,
        LocalDate toDate,
        String search
    ) {

        String sql = LEDGER_CTE + """
            SELECT COUNT(*)
            FROM filtered_entries
            """;

        String pattern = search == null
            ? null
            : "%" + search.trim() + "%";

        Long count = jdbcTemplate.queryForObject(
            sql,
            Long.class,
            fromDate,
            toDate,
            pattern,
            pattern,
            pattern,
            fromDate,
            pattern,
            pattern,
            pattern
        );

        return count == null ? 0 : count;
    }
}