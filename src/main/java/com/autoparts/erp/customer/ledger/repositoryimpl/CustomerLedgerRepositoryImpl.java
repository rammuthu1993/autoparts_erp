package com.autoparts.erp.customer.ledger.repositoryimpl;

import com.autoparts.erp.customer.ledger.dto.CustomerLedgerEntry;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;
import com.autoparts.erp.customer.ledger.repository.CustomerLedgerRepository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerLedgerRepositoryImpl
        implements CustomerLedgerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerLedgerRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
                WHERE uuid = :customerUuid
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("customerUuid", customerUuid),
                (rs, rowNum) -> new CustomerInfo(
                        rs.getLong("id"),
                        rs.getObject("uuid", UUID.class),
                        rs.getString("customer_name")
                )
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
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND s.sale_date < :fromDate

                    UNION ALL

                    -- Customer payments
                    SELECT -sp.amount AS transaction_amount
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND CAST(sp.payment_date AS DATE) < :fromDate

                    UNION ALL

                    -- Sales returns
                    SELECT -sr.total_return_amount AS transaction_amount
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                      AND CAST(sr.return_date AS DATE) < :fromDate

                    UNION ALL

                    -- Sales return refunds
                    SELECT sf.amount AS transaction_amount
                    FROM sales_refunds sf
                    JOIN sales_returns sr ON sr.id = sf.sales_return_id
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND CAST(sf.refund_date AS DATE) < :fromDate

                    UNION ALL

                    -- Issued invoice cancellations
                    SELECT -sic.cancelled_invoice_amount AS transaction_amount
                    FROM sales_invoice_cancellations sic
                    JOIN sales s ON s.id = sic.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'CANCELLED'
                      AND CAST(sic.cancelled_at AS DATE) < :fromDate

                    UNION ALL

                    -- Cancellation refunds
                    SELECT scr.amount AS transaction_amount
                    FROM sales_cancellation_refunds scr
                    JOIN sales s ON s.id = scr.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'CANCELLED'
                      AND scr.status NOT IN ('FAILED', 'REVERSED')
                      AND CAST(scr.refund_date AS DATE) < :fromDate

                ) transactions
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("fromDate", fromDate);

        BigDecimal result = jdbcTemplate.queryForObject(
                sql,
                params,
                BigDecimal.class
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
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND (CAST(:fromDate AS DATE) IS NULL OR s.sale_date >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR s.sale_date <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Payments
                    SELECT
                        0::numeric AS sales_amount,
                        sp.amount AS payment_amount,
                        0::numeric AS sales_return_amount
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(sp.payment_date AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(sp.payment_date AS DATE) <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Sales returns
                    SELECT
                        0::numeric AS sales_amount,
                        0::numeric AS payment_amount,
                        sr.total_return_amount AS sales_return_amount
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(sr.return_date AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(sr.return_date AS DATE) <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Sales return refunds
                    SELECT
                        sf.amount AS sales_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS sales_return_amount
                    FROM sales_refunds sf
                    JOIN sales_returns sr ON sr.id = sf.sales_return_id
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(sf.refund_date AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(sf.refund_date AS DATE) <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Issued invoice cancellations
                    SELECT
                        0::numeric AS sales_amount,
                        0::numeric AS payment_amount,
                        sic.cancelled_invoice_amount AS sales_return_amount
                    FROM sales_invoice_cancellations sic
                    JOIN sales s ON s.id = sic.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'CANCELLED'
                      AND sic.cancelled_invoice_amount > 0
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(sic.cancelled_at AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(sic.cancelled_at AS DATE) <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Cancellation refunds
                    SELECT
                        scr.amount AS sales_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS sales_return_amount
                    FROM sales_cancellation_refunds scr
                    JOIN sales s ON s.id = scr.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'CANCELLED'
                      AND scr.status NOT IN ('FAILED', 'REVERSED')
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(scr.refund_date AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(scr.refund_date AS DATE) <= CAST(:toDate AS DATE))

                ) transactions
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customer.id())
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate);

        return jdbcTemplate.queryForObject(
                sql,
                params,
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
                }
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
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))

                    UNION ALL

                    -- Payments
                    SELECT
                        sp.uuid AS transaction_uuid,
                        'PAYMENT' AS transaction_type,
                        COALESCE(
                            sp.reference_number,
                            s.invoice_number
                        ) AS reference_number,
                        CAST(sp.payment_date AS DATE) AS transaction_date,
                        0::numeric AS sales_amount,
                        sp.amount AS payment_amount,
                        0::numeric AS sales_return_amount,
                        -sp.amount AS signed_amount,
                        2 AS sort_order,
                        sp.id AS sort_id
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))

                    UNION ALL

                    -- Sales returns
                    SELECT
                        sr.uuid AS transaction_uuid,
                        'SALES_RETURN' AS transaction_type,
                        s.invoice_number AS reference_number,
                        CAST(sr.return_date AS DATE) AS transaction_date,
                        0::numeric AS sales_amount,
                        0::numeric AS payment_amount,
                        sr.total_return_amount AS sales_return_amount,
                        -sr.total_return_amount AS signed_amount,
                        3 AS sort_order,
                        sr.id AS sort_id
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )

                    UNION ALL

                    -- Sales return refunds
                    SELECT
                        sf.uuid AS transaction_uuid,
                        'SALES_REFUND' AS transaction_type,
                        COALESCE(sf.reference_number, s.invoice_number) AS reference_number,
                        CAST(sf.refund_date AS DATE) AS transaction_date,
                        sf.amount AS sales_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS sales_return_amount,
                        sf.amount AS signed_amount,
                        4 AS sort_order,
                        sf.id AS sort_id
                    FROM sales_refunds sf
                    JOIN sales_returns sr ON sr.id = sf.sales_return_id
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))

                    UNION ALL

                    -- Issued invoice cancellations
                    SELECT
                        sic.uuid AS transaction_uuid,
                        'INVOICE_CANCELLATION' AS transaction_type,
                        s.invoice_number AS reference_number,
                        CAST(sic.cancelled_at AS DATE) AS transaction_date,
                        0::numeric AS sales_amount,
                        0::numeric AS payment_amount,
                        sic.cancelled_invoice_amount AS sales_return_amount,
                        -sic.cancelled_invoice_amount AS signed_amount,
                        5 AS sort_order,
                        sic.id AS sort_id
                    FROM sales_invoice_cancellations sic
                    JOIN sales s ON s.id = sic.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'CANCELLED'
                      AND sic.cancelled_invoice_amount > 0

                    UNION ALL

                    -- Cancellation refunds
                    SELECT
                        scr.uuid AS transaction_uuid,
                        'CANCELLATION_REFUND' AS transaction_type,
                        COALESCE(scr.reference_number, s.invoice_number) AS reference_number,
                        CAST(scr.refund_date AS DATE) AS transaction_date,
                        scr.amount AS sales_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS sales_return_amount,
                        scr.amount AS signed_amount,
                        6 AS sort_order,
                        scr.id AS sort_id
                    FROM sales_cancellation_refunds scr
                    JOIN sales s ON s.id = scr.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'CANCELLED'
                      AND scr.status NOT IN ('FAILED', 'REVERSED')
                ),

                period_transactions AS (
                    SELECT *
                    FROM transactions
                    WHERE (
                        CAST(:fromDate AS DATE) IS NULL
                        OR transaction_date >= CAST(:fromDate AS DATE)
                    )
                    AND (
                        CAST(:toDate AS DATE) IS NULL
                        OR transaction_date <= CAST(:toDate AS DATE)
                    )
                ),

                running AS (
                    SELECT
                        *,
                        CAST(:openingBalance AS NUMERIC) + SUM(signed_amount) OVER (
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
                LIMIT :limit OFFSET :offset
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("openingBalance", openingBalance)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbcTemplate.query(
                sql,
                params,
                entryRowMapper
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
                    WHERE s.customer_id = :customerId
                      AND s.status = 'COMPLETED'
                      AND (CAST(:fromDate AS DATE) IS NULL OR s.sale_date >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR s.sale_date <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Payments
                    SELECT sp.id
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'COMPLETED'
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(sp.payment_date AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(sp.payment_date AS DATE) <= CAST(:toDate AS DATE))

                    UNION ALL

                    -- Sales returns
                    SELECT sr.id
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = :customerId
                      AND s.status = 'COMPLETED'
                      AND sr.status IN (
                          'ACCEPTED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                      AND (CAST(:fromDate AS DATE) IS NULL OR CAST(sr.return_date AS DATE) >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR CAST(sr.return_date AS DATE) <= CAST(:toDate AS DATE))

                ) transactions
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate);

        Long count = jdbcTemplate.queryForObject(
                sql,
                params,
                Long.class
        );

        return count != null ? count : 0L;
    }
}
