package com.autoparts.erp.customer.ledger.repositoryimpl;

import com.autoparts.erp.customer.ledger.dto.CustomerLedgerEntry;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;
import com.autoparts.erp.customer.ledger.repository.CustomerLedgerRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerLedgerRepositoryImpl
        implements CustomerLedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerLedgerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CustomerLedgerEntry> entryRowMapper =
            (rs, rowNum) -> new CustomerLedgerEntry(
                    rs.getObject("transaction_uuid", UUID.class),
                    rs.getString("transaction_type"),
                    rs.getString("reference_number"),
                    rs.getObject("transaction_date", LocalDate.class),
                    rs.getBigDecimal("sales_amount"),
                    rs.getBigDecimal("payment_amount"),
                    rs.getBigDecimal("sales_return_amount"),
                    rs.getBigDecimal("balance")
            );

    @Override
    public Optional<CustomerInfo> findCustomerByUuid(UUID customerUuid) {

        String sql = """
                SELECT id, uuid, customer_name
                FROM customers
                WHERE uuid = ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CustomerInfo(
                        rs.getLong("id"),
                        rs.getObject("uuid", UUID.class),
                        rs.getString("customer_name")
                ),
                customerUuid
        ).stream().findFirst();
    }

    /**
     * Opening balance =
     * completed sales before fromDate
     * - customer payments before fromDate
     * - accepted sales returns before fromDate.
     */
    @Override
    public BigDecimal getOpeningBalance(
            Long customerId,
            LocalDate fromDate
    ) {

        if (fromDate == null) {
            return BigDecimal.ZERO;
        }

        String sql = """
                SELECT COALESCE(SUM(transaction_amount), 0)
                FROM (

                    -- Sales
                    SELECT s.grand_total AS transaction_amount
                    FROM sales s
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND s.sale_date < ?

                    UNION ALL

                    -- Customer payments
                    SELECT -sp.amount AS transaction_amount
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND sp.payment_date::date < ?

                    UNION ALL

                    -- Sales returns
                    SELECT -sr.total_return_amount AS transaction_amount
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                      AND sr.return_date::date < ?

                ) transactions
                """;

        BigDecimal result = jdbcTemplate.queryForObject(
                sql,
                BigDecimal.class,
                customerId,
                Date.valueOf(fromDate),
                customerId,
                Date.valueOf(fromDate),
                customerId,
                Date.valueOf(fromDate)
        );

        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    public CustomerLedgerSummary getSummary(
            CustomerInfo customer,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance
    ) {

        String sql = """
                SELECT
                    COALESCE(SUM(sales_amount), 0)
                        AS total_sales,

                    COALESCE(SUM(payment_amount), 0)
                        AS total_payments,

                    COALESCE(SUM(sales_return_amount), 0)
                        AS total_sales_returns

                FROM (

                    -- Sales
                    SELECT
                        s.grand_total AS sales_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS sales_return_amount
                    FROM sales s
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND (?::date IS NULL OR s.sale_date >= ?::date)
                      AND (?::date IS NULL OR s.sale_date <= ?::date)

                    UNION ALL

                    -- Payments
                    SELECT
                        0::numeric AS sales_amount,
                        sp.amount AS payment_amount,
                        0::numeric AS sales_return_amount
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND (?::date IS NULL OR sp.payment_date::date >= ?::date)
                      AND (?::date IS NULL OR sp.payment_date::date <= ?::date)

                    UNION ALL

                    -- Sales returns
                    SELECT
                        0::numeric AS sales_amount,
                        0::numeric AS payment_amount,
                        sr.total_return_amount AS sales_return_amount
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                      AND (?::date IS NULL OR sr.return_date::date >= ?::date)
                      AND (?::date IS NULL OR sr.return_date::date <= ?::date)

                ) transactions
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    BigDecimal sales =
                            rs.getBigDecimal("total_sales");

                    BigDecimal payments =
                            rs.getBigDecimal("total_payments");

                    BigDecimal salesReturns =
                            rs.getBigDecimal("total_sales_returns");

                    BigDecimal closingBalance = openingBalance
                            .add(sales)
                            .subtract(payments)
                            .subtract(salesReturns);

                    return new CustomerLedgerSummary(
                            customer.uuid(),
                            customer.customerName(),
                            fromDate,
                            toDate,
                            openingBalance,
                            sales,
                            payments,
                            salesReturns,
                            closingBalance
                    );
                },

                // Sales
                customer.id(),
                fromDate, fromDate,
                toDate, toDate,

                // Payments
                customer.id(),
                fromDate, fromDate,
                toDate, toDate,

                // Sales returns
                customer.id(),
                fromDate, fromDate,
                toDate, toDate
        );
    }

    @Override
    public List<CustomerLedgerEntry> findEntries(
            Long customerId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance,
            int limit,
            int offset
    ) {

        String sql = """
                WITH transactions AS (

                    -- Sales
                    SELECT
                        s.uuid AS transaction_uuid,
                        'SALE' AS transaction_type,
                        s.invoice_number AS reference_number,
                        s.sale_date AS transaction_date,
                        s.grand_total AS sales_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS sales_return_amount,
                        s.grand_total AS signed_amount,
                        1 AS sort_order,
                        s.id AS sort_id
                    FROM sales s
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'

                    UNION ALL

                    -- Payments
                    SELECT
                        sp.uuid AS transaction_uuid,
                        'PAYMENT' AS transaction_type,
                        COALESCE(
                            sp.reference_number,
                            s.invoice_number
                        ) AS reference_number,
                        sp.payment_date::date AS transaction_date,
                        0::numeric AS sales_amount,
                        sp.amount AS payment_amount,
                        0::numeric AS sales_return_amount,
                        -sp.amount AS signed_amount,
                        2 AS sort_order,
                        sp.id AS sort_id
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'

                    UNION ALL

                    -- Sales returns
                    SELECT
                        sr.uuid AS transaction_uuid,
                        'SALES_RETURN' AS transaction_type,
                        s.invoice_number AS reference_number,
                        sr.return_date::date AS transaction_date,
                        0::numeric AS sales_amount,
                        0::numeric AS payment_amount,
                        sr.total_return_amount AS sales_return_amount,
                        -sr.total_return_amount AS signed_amount,
                        3 AS sort_order,
                        sr.id AS sort_id
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                ),

                period_transactions AS (
                    SELECT *
                    FROM transactions
                    WHERE (
                        ?::date IS NULL
                        OR transaction_date >= ?::date
                    )
                    AND (
                        ?::date IS NULL
                        OR transaction_date <= ?::date
                    )
                ),

                running AS (
                    SELECT
                        *,
                        ?::numeric + SUM(signed_amount) OVER (
                            ORDER BY
                                transaction_date,
                                sort_order,
                                sort_id
                            ROWS BETWEEN UNBOUNDED PRECEDING
                                     AND CURRENT ROW
                        ) AS balance
                    FROM period_transactions
                )

                SELECT
                    transaction_uuid,
                    transaction_type,
                    reference_number,
                    transaction_date,
                    sales_amount,
                    payment_amount,
                    sales_return_amount,
                    balance
                FROM running
                ORDER BY
                    transaction_date DESC,
                    sort_order DESC,
                    sort_id DESC
                LIMIT ? OFFSET ?
                """;

        return jdbcTemplate.query(
                sql,
                entryRowMapper,
                customerId,
                customerId,
                customerId,
                fromDate, fromDate,
                toDate, toDate,
                openingBalance,
                limit,
                offset
        );
    }

    @Override
    public long countEntries(
            Long customerId,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        String sql = """
                SELECT COUNT(*)
                FROM (

                    -- Sales
                    SELECT s.id
                    FROM sales s
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND (?::date IS NULL OR s.sale_date >= ?::date)
                      AND (?::date IS NULL OR s.sale_date <= ?::date)

                    UNION ALL

                    -- Payments
                    SELECT sp.id
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND (?::date IS NULL OR sp.payment_date::date >= ?::date)
                      AND (?::date IS NULL OR sp.payment_date::date <= ?::date)

                    UNION ALL

                    -- Sales returns
                    SELECT sr.id
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = ?
                      AND s.status = 'COMPLETED'
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                      AND (?::date IS NULL OR sr.return_date::date >= ?::date)
                      AND (?::date IS NULL OR sr.return_date::date <= ?::date)

                ) transactions
                """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,

                // Sales
                customerId,
                fromDate, fromDate,
                toDate, toDate,

                // Payments
                customerId,
                fromDate, fromDate,
                toDate, toDate,

                // Sales returns
                customerId,
                fromDate, fromDate,
                toDate, toDate
        );

        return count != null ? count : 0L;
    }
}