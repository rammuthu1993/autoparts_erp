package com.autoparts.erp.reports.repositoryimpl;

import com.autoparts.erp.reports.dto.DashboardSummaryResponse.ExpenseSummary;
import com.autoparts.erp.reports.dto.DashboardSummaryResponse.PurchaseSummary;
import com.autoparts.erp.reports.dto.DashboardSummaryResponse.SalesSummary;
import com.autoparts.erp.reports.repository.ReportRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepositoryImpl implements ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------
    // SALES SUMMARY
    // -------------------------------------------------

    @Override
    public SalesSummary getSalesSummary(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp) {

        var params = new MapSqlParameterSource()
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("fromTimestamp", fromTimestamp)
                .addValue("toTimestamp", toTimestamp);

        String invoiceSql = """
                SELECT
                    COUNT(*) AS invoice_count,
                    COALESCE(SUM(grand_total), 0) AS invoice_total,
                    COALESCE(SUM(paid_amount), 0) AS paid_total,
                    COALESCE(SUM(balance_amount), 0) AS outstanding_total
                FROM sales
                WHERE sale_date >= :fromDate
                  AND sale_date <= :toDate
                  AND status = 'COMPLETED'
                """;

        InvoiceTotals invoices = jdbc.queryForObject(
                invoiceSql,
                params,
                (rs, rowNum) -> new InvoiceTotals(
                        rs.getLong("invoice_count"),
                        rs.getBigDecimal("invoice_total"),
                        rs.getBigDecimal("paid_total"),
                        rs.getBigDecimal("outstanding_total")
                )
        );

        String paymentSql = """
                SELECT
                    COALESCE(SUM(sp.amount), 0) AS collection_total,

                    COALESCE(SUM(sp.amount) FILTER (
                        WHERE sp.payment_method = 'CASH'
                    ), 0) AS cash_total,

                    COALESCE(SUM(sp.amount) FILTER (
                        WHERE sp.payment_method = 'UPI'
                    ), 0) AS upi_total,

                    COALESCE(SUM(sp.amount) FILTER (
                        WHERE sp.payment_method = 'CARD'
                    ), 0) AS card_total,

                    COALESCE(SUM(sp.amount) FILTER (
                        WHERE sp.payment_method = 'BANK'
                    ), 0) AS bank_total,

                    COALESCE(SUM(sp.amount) FILTER (
                        WHERE sp.payment_method = 'CREDIT'
                    ), 0) AS credit_total

                FROM sales_payments sp

                JOIN sales s
                    ON s.id = sp.sale_id

                WHERE sp.payment_date >= :fromTimestamp
                  AND sp.payment_date < :toTimestamp
                  AND s.status = 'COMPLETED'
                """;

        PaymentTotals payments = jdbc.queryForObject(
                paymentSql,
                params,
                (rs, rowNum) -> new PaymentTotals(
                        rs.getBigDecimal("collection_total"),
                        rs.getBigDecimal("cash_total"),
                        rs.getBigDecimal("upi_total"),
                        rs.getBigDecimal("card_total"),
                        rs.getBigDecimal("bank_total"),
                        rs.getBigDecimal("credit_total")
                )
        );

        return new SalesSummary(
                invoices.invoiceCount(),
                invoices.invoiceTotal(),
                invoices.paidTotal(),
                invoices.outstandingTotal(),
                payments.total(),
                payments.cash(),
                payments.upi(),
                payments.card(),
                payments.bank(),
                payments.credit()
        );
    }

    // -------------------------------------------------
    // PURCHASE SUMMARY
    // -------------------------------------------------

    @Override
    public PurchaseSummary getPurchaseSummary(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp) {

        var params = new MapSqlParameterSource()
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("fromTimestamp", fromTimestamp)
                .addValue("toTimestamp", toTimestamp);

        String purchaseSql = """
                SELECT
                    COUNT(*) AS purchase_count,
                    COALESCE(SUM(grand_total), 0) AS purchase_total

                FROM purchases

                WHERE purchase_date >= :fromDate
                  AND purchase_date <= :toDate
                  AND status = 'RECEIVED'
                """;

        PurchaseTotals purchases = jdbc.queryForObject(
                purchaseSql,
                params,
                (rs, rowNum) -> new PurchaseTotals(
                        rs.getLong("purchase_count"),
                        rs.getBigDecimal("purchase_total")
                )
        );

        String paymentSql = """
                SELECT
                    COALESCE(SUM(pp.amount), 0) AS payment_total,

                    COALESCE(SUM(pp.amount) FILTER (
                        WHERE pp.payment_method = 'CASH'
                    ), 0) AS cash_total,

                    COALESCE(SUM(pp.amount) FILTER (
                        WHERE pp.payment_method = 'UPI'
                    ), 0) AS upi_total,

                    COALESCE(SUM(pp.amount) FILTER (
                        WHERE pp.payment_method = 'CARD'
                    ), 0) AS card_total,

                    COALESCE(SUM(pp.amount) FILTER (
                        WHERE pp.payment_method = 'BANK'
                    ), 0) AS bank_total

                FROM purchase_payments pp

                JOIN purchases p
                    ON p.id = pp.purchase_id

                WHERE pp.payment_date >= :fromTimestamp
                  AND pp.payment_date < :toTimestamp
                  AND p.status = 'RECEIVED'
                """;

        PurchasePaymentTotals payments = jdbc.queryForObject(
                paymentSql,
                params,
                (rs, rowNum) -> new PurchasePaymentTotals(
                        rs.getBigDecimal("payment_total"),
                        rs.getBigDecimal("cash_total"),
                        rs.getBigDecimal("upi_total"),
                        rs.getBigDecimal("card_total"),
                        rs.getBigDecimal("bank_total")
                )
        );

        return new PurchaseSummary(
                purchases.purchaseCount(),
                purchases.purchaseTotal(),
                payments.total(),
                payments.cash(),
                payments.upi(),
                payments.card(),
                payments.bank()
        );
    }

    // -------------------------------------------------
    // EXPENSE SUMMARY
    // -------------------------------------------------

    @Override
    public ExpenseSummary getExpenseSummary(
            LocalDate fromDate,
            LocalDate toDate) {

        Map<String, LocalDate> params = Map.of(
                "fromDate", fromDate,
                "toDate", toDate
        );

        String sql = """
                SELECT
                    COUNT(*) AS expense_count,
                    COALESCE(SUM(amount), 0) AS expense_total,

                    COALESCE(SUM(amount) FILTER (
                        WHERE payment_method = 'CASH'
                    ), 0) AS cash_total,

                    COALESCE(SUM(amount) FILTER (
                        WHERE payment_method = 'UPI'
                    ), 0) AS upi_total,

                    COALESCE(SUM(amount) FILTER (
                        WHERE payment_method = 'CARD'
                    ), 0) AS card_total,

                    COALESCE(SUM(amount) FILTER (
                        WHERE payment_method = 'BANK'
                    ), 0) AS bank_total

                FROM expenses

                WHERE expense_date >= :fromDate
                  AND expense_date <= :toDate
                  AND status = 'ACTIVE'
                """;

        return jdbc.queryForObject(
                sql,
                params,
                (rs, rowNum) -> new ExpenseSummary(
                        rs.getLong("expense_count"),
                        rs.getBigDecimal("expense_total"),
                        rs.getBigDecimal("cash_total"),
                        rs.getBigDecimal("upi_total"),
                        rs.getBigDecimal("card_total"),
                        rs.getBigDecimal("bank_total")
                )
        );
    }

    // -------------------------------------------------
    // INTERNAL RECORDS
    // -------------------------------------------------

    private record InvoiceTotals(
            long invoiceCount,
            BigDecimal invoiceTotal,
            BigDecimal paidTotal,
            BigDecimal outstandingTotal
    ) {}

    private record PaymentTotals(
            BigDecimal total,
            BigDecimal cash,
            BigDecimal upi,
            BigDecimal card,
            BigDecimal bank,
            BigDecimal credit
    ) {}

    private record PurchaseTotals(
            long purchaseCount,
            BigDecimal purchaseTotal
    ) {}

    private record PurchasePaymentTotals(
            BigDecimal total,
            BigDecimal cash,
            BigDecimal upi,
            BigDecimal card,
            BigDecimal bank
    ) {}
}