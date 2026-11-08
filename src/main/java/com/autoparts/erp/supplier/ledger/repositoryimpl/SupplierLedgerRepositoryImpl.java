package com.autoparts.erp.supplier.ledger.repositoryimpl;

import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerEntry;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerSummary;
import com.autoparts.erp.supplier.ledger.repository.SupplierLedgerRepository;
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
public class SupplierLedgerRepositoryImpl
        implements SupplierLedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public SupplierLedgerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SupplierLedgerEntry> entryRowMapper =
            (rs, rowNum) -> new SupplierLedgerEntry(
                    rs.getObject("transaction_uuid", UUID.class),
                    rs.getString("transaction_type"),
                    rs.getString("reference_number"),
                    rs.getObject("transaction_date", LocalDate.class),
                    rs.getBigDecimal("purchase_amount"),
                    rs.getBigDecimal("payment_amount"),
                    rs.getBigDecimal("purchase_return_amount"),
                    rs.getBigDecimal("balance")
            );

    @Override
    public Optional<SupplierInfo> findSupplierByUuid(UUID supplierUuid) {

        String sql = """
                SELECT id, uuid, supplier_name
                FROM suppliers
                WHERE uuid = ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SupplierInfo(
                        rs.getLong("id"),
                        rs.getObject("uuid", UUID.class),
                        rs.getString("supplier_name")
                ),
                supplierUuid
        ).stream().findFirst();
    }

    /**
     * Opening balance =
     * purchases before fromDate
     * - payments before fromDate
     * - purchase returns before fromDate.
     */
    @Override
    public BigDecimal getOpeningBalance(
            Long supplierId,
            LocalDate fromDate
    ) {

        if (fromDate == null) {
            return BigDecimal.ZERO;
        }

        String sql = """
                SELECT COALESCE(SUM(transaction_amount), 0)
                FROM (
                    SELECT p.grand_total AS transaction_amount
                    FROM purchases p
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND p.purchase_date < ?

                    UNION ALL

                    SELECT -pp.amount AS transaction_amount
                    FROM purchase_payments pp
                    JOIN purchases p ON p.id = pp.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND pp.payment_date::date < ?

                    UNION ALL

                    SELECT -pr.total_return_amount AS transaction_amount
                    FROM purchase_returns pr
                    JOIN purchases p ON p.id = pr.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND pr.return_date::date < ?
                ) transactions
                """;

        BigDecimal result = jdbcTemplate.queryForObject(
                sql,
                BigDecimal.class,
                supplierId,
                Date.valueOf(fromDate),
                supplierId,
                Date.valueOf(fromDate),
                supplierId,
                Date.valueOf(fromDate)
        );

        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    public SupplierLedgerSummary getSummary(
            SupplierInfo supplier,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance
    ) {

        String sql = """
                SELECT
                    COALESCE(SUM(purchase_amount), 0)
                        AS total_purchases,
                    COALESCE(SUM(payment_amount), 0)
                        AS total_payments,
                    COALESCE(SUM(purchase_return_amount), 0)
                        AS total_purchase_returns
                FROM (

                    -- Purchases
                    SELECT
                        p.grand_total AS purchase_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS purchase_return_amount
                    FROM purchases p
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND (?::date IS NULL OR p.purchase_date >= ?::date)
                      AND (?::date IS NULL OR p.purchase_date <= ?::date)

                    UNION ALL

                    -- Payments
                    SELECT
                        0::numeric AS purchase_amount,
                        pp.amount AS payment_amount,
                        0::numeric AS purchase_return_amount
                    FROM purchase_payments pp
                    JOIN purchases p ON p.id = pp.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND (?::date IS NULL OR pp.payment_date::date >= ?::date)
                      AND (?::date IS NULL OR pp.payment_date::date <= ?::date)

                    UNION ALL

                    -- Purchase returns
                    SELECT
                        0::numeric AS purchase_amount,
                        0::numeric AS payment_amount,
                        pr.total_return_amount AS purchase_return_amount
                    FROM purchase_returns pr
                    JOIN purchases p ON p.id = pr.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND (?::date IS NULL OR pr.return_date::date >= ?::date)
                      AND (?::date IS NULL OR pr.return_date::date <= ?::date)

                ) transactions
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    BigDecimal purchases =
                            rs.getBigDecimal("total_purchases");

                    BigDecimal payments =
                            rs.getBigDecimal("total_payments");

                    BigDecimal purchaseReturns =
                            rs.getBigDecimal("total_purchase_returns");

                    BigDecimal closingBalance = openingBalance
                            .add(purchases)
                            .subtract(payments)
                            .subtract(purchaseReturns);

                    return new SupplierLedgerSummary(
                            supplier.uuid(),
                            supplier.supplierName(),
                            fromDate,
                            toDate,
                            openingBalance,
                            purchases,
                            payments,
                            purchaseReturns,
                            closingBalance
                    );
                },

                // Purchase filters
                supplier.id(),
                fromDate, fromDate,
                toDate, toDate,

                // Payment filters
                supplier.id(),
                fromDate, fromDate,
                toDate, toDate,

                // Purchase return filters
                supplier.id(),
                fromDate, fromDate,
                toDate, toDate
        );
    }

    @Override
    public List<SupplierLedgerEntry> findEntries(
            Long supplierId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance,
            int limit,
            int offset
    ) {

        String sql = """
                WITH transactions AS (

                    -- Purchases
                    SELECT
                        p.uuid AS transaction_uuid,
                        'PURCHASE' AS transaction_type,
                        p.purchase_number AS reference_number,
                        p.purchase_date AS transaction_date,
                        p.grand_total AS purchase_amount,
                        0::numeric AS payment_amount,
                        0::numeric AS purchase_return_amount,
                        p.grand_total AS signed_amount,
                        1 AS sort_order,
                        p.id AS sort_id
                    FROM purchases p
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'

                    UNION ALL

                    -- Payments
                    SELECT
                        pp.uuid AS transaction_uuid,
                        'PAYMENT' AS transaction_type,
                        COALESCE(
                            pp.reference_number,
                            p.purchase_number
                        ) AS reference_number,
                        pp.payment_date::date AS transaction_date,
                        0::numeric AS purchase_amount,
                        pp.amount AS payment_amount,
                        0::numeric AS purchase_return_amount,
                        -pp.amount AS signed_amount,
                        2 AS sort_order,
                        pp.id AS sort_id
                    FROM purchase_payments pp
                    JOIN purchases p ON p.id = pp.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'

                    UNION ALL

                    -- Purchase returns
                    SELECT
                        pr.uuid AS transaction_uuid,
                        'PURCHASE_RETURN' AS transaction_type,
                        pr.return_number AS reference_number,
                        pr.return_date::date AS transaction_date,
                        0::numeric AS purchase_amount,
                        0::numeric AS payment_amount,
                        pr.total_return_amount AS purchase_return_amount,
                        -pr.total_return_amount AS signed_amount,
                        3 AS sort_order,
                        pr.id AS sort_id
                    FROM purchase_returns pr
                    JOIN purchases p ON p.id = pr.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                ),

                period_transactions AS (
                    SELECT *
                    FROM transactions
                    WHERE (?::date IS NULL OR transaction_date >= ?::date)
                      AND (?::date IS NULL OR transaction_date <= ?::date)
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
                    purchase_amount,
                    payment_amount,
                    purchase_return_amount,
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
                supplierId,
                supplierId,
                supplierId,
                fromDate, fromDate,
                toDate, toDate,
                openingBalance,
                limit,
                offset
        );
    }

    @Override
    public long countEntries(
            Long supplierId,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        String sql = """
                SELECT COUNT(*)
                FROM (

                    SELECT p.id
                    FROM purchases p
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND (?::date IS NULL OR p.purchase_date >= ?::date)
                      AND (?::date IS NULL OR p.purchase_date <= ?::date)

                    UNION ALL

                    SELECT pp.id
                    FROM purchase_payments pp
                    JOIN purchases p ON p.id = pp.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND (?::date IS NULL OR pp.payment_date::date >= ?::date)
                      AND (?::date IS NULL OR pp.payment_date::date <= ?::date)

                    UNION ALL

                    SELECT pr.id
                    FROM purchase_returns pr
                    JOIN purchases p ON p.id = pr.purchase_id
                    WHERE p.supplier_id = ?
                      AND p.status = 'RECEIVED'
                      AND (?::date IS NULL OR pr.return_date::date >= ?::date)
                      AND (?::date IS NULL OR pr.return_date::date <= ?::date)

                ) transactions
                """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,

                // Purchase filters
                supplierId,
                fromDate, fromDate,
                toDate, toDate,

                // Payment filters
                supplierId,
                fromDate, fromDate,
                toDate, toDate,

                // Purchase return filters
                supplierId,
                fromDate, fromDate,
                toDate, toDate
        );

        return count != null ? count : 0L;
    }
}