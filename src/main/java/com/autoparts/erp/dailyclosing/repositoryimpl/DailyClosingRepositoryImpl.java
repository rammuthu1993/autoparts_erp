
package com.autoparts.erp.dailyclosing.repositoryimpl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.dailyclosing.dto.CashRegisterClosingResponse;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.dto.DailyClosingSummary;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;

@Repository
public class DailyClosingRepositoryImpl
        implements DailyClosingRepository {

    private final JdbcTemplate jdbcTemplate;

    public DailyClosingRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SUMMARY_SELECT = """
        SELECT *
        FROM daily_closings
        """;

    @Override
    public boolean existsByBusinessDate(LocalDate businessDate) {

        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM daily_closings
                WHERE business_date = ?
            )
            """,
            Boolean.class,
            businessDate
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean hasOpenSessions(
        OffsetDateTime start,
        OffsetDateTime end
    ) {

        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM cash_register_sessions
                WHERE opened_at >= ?
                  AND opened_at < ?
                  AND status = 'OPEN'
            )
            """,
            Boolean.class,
            start,
            end
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public DailyClosingResponse createClosing(
        LocalDate businessDate,
        OffsetDateTime start,
        OffsetDateTime end,
        Long closedBy,
        String notes
    ) {

        UUID closingUuid = UUID.randomUUID();

        DailyClosingSummary summary = calculateSummary(
            businessDate,
            start,
            end
        );

        Long closingId = jdbcTemplate.queryForObject(
            """
            INSERT INTO daily_closings (
                uuid,
                business_date,
                closed_by,
                notes,

                sales_count,
                sales_total,
                sales_paid_total,
                sales_balance_total,

                sales_cash_total,
                sales_upi_total,
                sales_card_total,
                sales_bank_total,
                sales_credit_total,

                sales_return_count,
                sales_return_total,

                sales_refund_total,
                sales_refund_cash_total,
                sales_refund_upi_total,
                sales_refund_card_total,
                sales_refund_bank_total,
                sales_refund_credit_total,

                purchase_count,
                purchase_total,

                purchase_cash_total,
                purchase_upi_total,
                purchase_card_total,
                purchase_bank_total,

                expense_count,
                expense_total,

                expense_cash_total,
                expense_upi_total,
                expense_card_total,
                expense_bank_total,

                register_count,
                cash_opening_total,
                cash_in_total,
                cash_out_total,
                cash_expected_total,
                cash_actual_total,
                cash_difference_total
            )
            VALUES (
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?
            )
            RETURNING id
            """,
            Long.class,

            closingUuid,
            businessDate,
            closedBy,
            notes,

            summary.salesCount(),
            summary.salesTotal(),
            summary.salesPaidTotal(),
            summary.salesBalanceTotal(),

            summary.salesCashTotal(),
            summary.salesUpiTotal(),
            summary.salesCardTotal(),
            summary.salesBankTotal(),
            summary.salesCreditTotal(),

            summary.salesReturnCount(),
            summary.salesReturnTotal(),

            summary.salesRefundTotal(),
            summary.salesRefundCashTotal(),
            summary.salesRefundUpiTotal(),
            summary.salesRefundCardTotal(),
            summary.salesRefundBankTotal(),
            summary.salesRefundCreditTotal(),

            summary.purchaseCount(),
            summary.purchaseTotal(),

            summary.purchaseCashTotal(),
            summary.purchaseUpiTotal(),
            summary.purchaseCardTotal(),
            summary.purchaseBankTotal(),

            summary.expenseCount(),
            summary.expenseTotal(),

            summary.expenseCashTotal(),
            summary.expenseUpiTotal(),
            summary.expenseCardTotal(),
            summary.expenseBankTotal(),

            summary.registerCount(),
            summary.cashOpeningTotal(),
            summary.cashInTotal(),
            summary.cashOutTotal(),
            summary.cashExpectedTotal(),
            summary.cashActualTotal(),
            summary.cashDifferenceTotal()
        );

        saveRegisterDetails(closingId, start, end);

        return findByBusinessDate(businessDate)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Daily closing was created but could not be retrieved"
                )
            );
    }

    private DailyClosingSummary calculateSummary(
        LocalDate businessDate,
        OffsetDateTime start,
        OffsetDateTime end
    ) {

        // -------------------------------------------------
        // SALES
        // -------------------------------------------------

        DailyClosingSummary sales = jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) AS sales_count,
                COALESCE(SUM(grand_total), 0) AS sales_total,
                COALESCE(SUM(paid_amount), 0) AS sales_paid_total,
                COALESCE(SUM(balance_amount), 0) AS sales_balance_total
            FROM sales
            WHERE sale_date = ?
              AND status = 'COMPLETED'
            """,
            (rs, rowNum) -> new DailyClosingSummary(
                rs.getLong("sales_count"),
                money(rs, "sales_total"),
                money(rs, "sales_paid_total"),
                money(rs, "sales_balance_total"),

                zero(), zero(), zero(), zero(), zero(),

                0L, zero(),

                zero(), zero(), zero(), zero(), zero(), zero(),

                0L, zero(),

                zero(), zero(), zero(), zero(),

                0L, zero(),

                zero(), zero(), zero(), zero(),

                0L, zero(), zero(), zero(), zero(), zero(), zero()
            ),
            businessDate
        );

        // -------------------------------------------------
        // SALES PAYMENTS
        // -------------------------------------------------

        PaymentTotals salesPayments = jdbcTemplate.queryForObject(
            """
            SELECT
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
                ), 0) AS bank_total,
                
                COALESCE(SUM(amount), 0) AS total_amount,
                
				COALESCE(SUM(amount) FILTER (
				    WHERE payment_method = 'CREDIT'
				), 0) AS credit_total,

                COALESCE(SUM(amount) FILTER (
                    WHERE payment_method = 'CREDIT'
                ), 0) AS credit_total
            FROM sales_payments
            WHERE payment_date >= ?
              AND payment_date < ?
            """,
            paymentTotalsMapper,
            start,
            end
        );

        // -------------------------------------------------
        // SALES RETURNS
        // -------------------------------------------------

        ReturnTotals returns = jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) AS return_count,
                COALESCE(SUM(total_return_amount), 0) AS return_total
            FROM sales_returns
            WHERE return_date >= ?
              AND return_date < ?
            """,
            (rs, rowNum) -> new ReturnTotals(
                rs.getLong("return_count"),
                money(rs, "return_total")
            ),
            start,
            end
        );

        // -------------------------------------------------
        // SALES REFUNDS
        // -------------------------------------------------

        PaymentTotals refunds = jdbcTemplate.queryForObject(
            """
            SELECT
                COALESCE(SUM(amount), 0) AS total_amount,

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
                ), 0) AS bank_total,

                COALESCE(SUM(amount) FILTER (
                    WHERE payment_method = 'CREDIT'
                ), 0) AS credit_total
            FROM sales_refunds
            WHERE refund_date >= ?
              AND refund_date < ?
            """,
            (rs, rowNum) -> new PaymentTotals(
                money(rs, "total_amount"),
                money(rs, "cash_total"),
                money(rs, "upi_total"),
                money(rs, "card_total"),
                money(rs, "bank_total"),
                money(rs, "credit_total")
            ),
            start,
            end
        );

        // -------------------------------------------------
        // PURCHASES
        // -------------------------------------------------

        PurchaseTotals purchases = jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) AS purchase_count,
                COALESCE(SUM(grand_total), 0) AS purchase_total
            FROM purchases
            WHERE purchase_date = ?
              AND status = 'RECEIVED'
            """,
            (rs, rowNum) -> new PurchaseTotals(
                rs.getLong("purchase_count"),
                money(rs, "purchase_total")
            ),
            businessDate
        );

        // -------------------------------------------------
        // PURCHASE PAYMENTS
        // -------------------------------------------------

        PaymentTotals purchasePayments = jdbcTemplate.queryForObject(
            """
            SELECT
                COALESCE(SUM(amount) FILTER (
                    WHERE payment_method = 'CASH'
                ), 0) AS cash_total,

                COALESCE(SUM(amount) FILTER (
                    WHERE payment_method = 'UPI'
                ), 0) AS upi_total,

                COALESCE(SUM(amount) FILTER (
                    WHERE payment_method = 'CARD'
                ), 0) AS card_total,
                
                COALESCE(SUM(amount), 0) AS total_amount,
        		0::NUMERIC AS credit_total,

                COALESCE(SUM(amount) FILTER (
                    WHERE payment_method = 'BANK'
                ), 0) AS bank_total
            FROM purchase_payments
            WHERE payment_date >= ?
              AND payment_date < ?
            """,
            paymentTotalsMapper,
            start,
            end
        );

        // -------------------------------------------------
        // EXPENSES
        // -------------------------------------------------

        ExpenseTotals expenses = jdbcTemplate.queryForObject(
            """
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
            WHERE expense_date = ?
              AND status = 'ACTIVE'
            """,
            (rs, rowNum) -> new ExpenseTotals(
                rs.getLong("expense_count"),
                money(rs, "expense_total"),
                money(rs, "cash_total"),
                money(rs, "upi_total"),
                money(rs, "card_total"),
                money(rs, "bank_total")
            ),
            businessDate
        );

        // -------------------------------------------------
        // CASH REGISTER SESSION TOTALS
        // -------------------------------------------------

        CashTotals cash = jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) AS register_count,

                COALESCE(SUM(s.opening_balance), 0)
                    AS opening_total,

                COALESCE(SUM(m.cash_in), 0)
                    AS cash_in_total,

                COALESCE(SUM(m.cash_out), 0)
                    AS cash_out_total,

                COALESCE(SUM(s.expected_closing_balance), 0)
                    AS expected_total,

                COALESCE(SUM(s.actual_closing_balance), 0)
                    AS actual_total,

                COALESCE(SUM(s.difference_amount), 0)
                    AS difference_total

            FROM cash_register_sessions s

            LEFT JOIN (
                SELECT
                    session_id,

                    COALESCE(SUM(amount) FILTER (
                        WHERE movement_type = 'IN'
                    ), 0) AS cash_in,

                    COALESCE(SUM(amount) FILTER (
                        WHERE movement_type = 'OUT'
                    ), 0) AS cash_out

                FROM cash_register_movements
                GROUP BY session_id
            ) m ON m.session_id = s.id

            WHERE s.opened_at >= ?
              AND s.opened_at < ?
              AND s.status = 'CLOSED'
            """,
            (rs, rowNum) -> new CashTotals(
                rs.getLong("register_count"),
                money(rs, "opening_total"),
                money(rs, "cash_in_total"),
                money(rs, "cash_out_total"),
                money(rs, "expected_total"),
                money(rs, "actual_total"),
                money(rs, "difference_total")
            ),
            start,
            end
        );

        return new DailyClosingSummary(
            sales.salesCount(),
            sales.salesTotal(),
            sales.salesPaidTotal(),
            sales.salesBalanceTotal(),

            salesPayments.cash(),
            salesPayments.upi(),
            salesPayments.card(),
            salesPayments.bank(),
            salesPayments.credit(),

            returns.count(),
            returns.total(),

            refunds.total(),
            refunds.cash(),
            refunds.upi(),
            refunds.card(),
            refunds.bank(),
            refunds.credit(),

            purchases.count(),
            purchases.total(),

            purchasePayments.cash(),
            purchasePayments.upi(),
            purchasePayments.card(),
            purchasePayments.bank(),

            expenses.count(),
            expenses.total(),
            expenses.cash(),
            expenses.upi(),
            expenses.card(),
            expenses.bank(),

            cash.count(),
            cash.opening(),
            cash.cashIn(),
            cash.cashOut(),
            cash.expected(),
            cash.actual(),
            cash.difference()
        );
    }

    private void saveRegisterDetails(
        Long closingId,
        OffsetDateTime start,
        OffsetDateTime end
    ) {

        List<CashRegisterClosingResponse> sessions =
            findRegisterSessions(start, end);

        for (CashRegisterClosingResponse session : sessions) {

            jdbcTemplate.update(
                """
                INSERT INTO daily_closing_cash_registers (
                    daily_closing_id,
                    cash_register_session_id,
                    register_name,
                    opening_balance,
                    total_cash_in,
                    total_cash_out,
                    expected_closing_balance,
                    actual_closing_balance,
                    difference_amount
                )
                SELECT
                    ?,
                    s.id,
                    r.name,
                    s.opening_balance,
                    ?,
                    ?,
                    s.expected_closing_balance,
                    s.actual_closing_balance,
                    s.difference_amount
                FROM cash_register_sessions s
                JOIN cash_registers r
                    ON r.id = s.cash_register_id
                WHERE s.uuid = ?
                """,
                closingId,
                session.totalCashIn(),
                session.totalCashOut(),
                session.sessionUuid()
            );
        }
    }

    private List<CashRegisterClosingResponse> findRegisterSessions(
        OffsetDateTime start,
        OffsetDateTime end
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                s.uuid AS session_uuid,
                r.name AS register_name,
                s.opening_balance,
                s.expected_closing_balance,
                s.actual_closing_balance,
                s.difference_amount,

                COALESCE(m.cash_in, 0) AS cash_in,
                COALESCE(m.cash_out, 0) AS cash_out

            FROM cash_register_sessions s

            JOIN cash_registers r
                ON r.id = s.cash_register_id

            LEFT JOIN (
                SELECT
                    session_id,

                    COALESCE(SUM(amount) FILTER (
                        WHERE movement_type = 'IN'
                    ), 0) AS cash_in,

                    COALESCE(SUM(amount) FILTER (
                        WHERE movement_type = 'OUT'
                    ), 0) AS cash_out

                FROM cash_register_movements
                GROUP BY session_id
            ) m ON m.session_id = s.id

            WHERE s.opened_at >= ?
              AND s.opened_at < ?
              AND s.status = 'CLOSED'

            ORDER BY r.name, s.opened_at
            """,
            (rs, rowNum) -> new CashRegisterClosingResponse(
                rs.getObject("session_uuid", UUID.class),
                rs.getString("register_name"),
                rs.getBigDecimal("opening_balance"),
                rs.getBigDecimal("cash_in"),
                rs.getBigDecimal("cash_out"),
                rs.getBigDecimal("expected_closing_balance"),
                rs.getBigDecimal("actual_closing_balance"),
                rs.getBigDecimal("difference_amount")
            ),
            start,
            end
        );
    }

    @Override
    public Optional<DailyClosingResponse> findByBusinessDate(
        LocalDate businessDate
    ) {

        return findOne(
            "WHERE business_date = ?",
            businessDate
        );
    }

    @Override
    public Optional<DailyClosingResponse> findByUuid(UUID uuid) {

        return findOne(
            "WHERE uuid = ?",
            uuid
        );
    }

    private Optional<DailyClosingResponse> findOne(
        String condition,
        Object value
    ) {

        try {

            return jdbcTemplate.query(
                SUMMARY_SELECT + " " + condition,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(mapResponse(rs));
                },
                value
            );

        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private DailyClosingResponse mapResponse(ResultSet rs)
            throws SQLException {

        Long closingId = rs.getLong("id");

        DailyClosingSummary summary = new DailyClosingSummary(
            rs.getLong("sales_count"),
            money(rs, "sales_total"),
            money(rs, "sales_paid_total"),
            money(rs, "sales_balance_total"),

            money(rs, "sales_cash_total"),
            money(rs, "sales_upi_total"),
            money(rs, "sales_card_total"),
            money(rs, "sales_bank_total"),
            money(rs, "sales_credit_total"),

            rs.getLong("sales_return_count"),
            money(rs, "sales_return_total"),

            money(rs, "sales_refund_total"),
            money(rs, "sales_refund_cash_total"),
            money(rs, "sales_refund_upi_total"),
            money(rs, "sales_refund_card_total"),
            money(rs, "sales_refund_bank_total"),
            money(rs, "sales_refund_credit_total"),

            rs.getLong("purchase_count"),
            money(rs, "purchase_total"),

            money(rs, "purchase_cash_total"),
            money(rs, "purchase_upi_total"),
            money(rs, "purchase_card_total"),
            money(rs, "purchase_bank_total"),

            rs.getLong("expense_count"),
            money(rs, "expense_total"),

            money(rs, "expense_cash_total"),
            money(rs, "expense_upi_total"),
            money(rs, "expense_card_total"),
            money(rs, "expense_bank_total"),

            rs.getLong("register_count"),

            money(rs, "cash_opening_total"),
            money(rs, "cash_in_total"),
            money(rs, "cash_out_total"),
            money(rs, "cash_expected_total"),
            money(rs, "cash_actual_total"),
            money(rs, "cash_difference_total")
        );

        List<CashRegisterClosingResponse> registers =
            jdbcTemplate.query(
                """
                SELECT
                    s.uuid AS session_uuid,
                    d.register_name,
                    d.opening_balance,
                    d.total_cash_in,
                    d.total_cash_out,
                    d.expected_closing_balance,
                    d.actual_closing_balance,
                    d.difference_amount
                FROM daily_closing_cash_registers d
                JOIN cash_register_sessions s
                    ON s.id = d.cash_register_session_id
                WHERE d.daily_closing_id = ?
                ORDER BY d.register_name
                """,
                (detailRs, rowNum) ->
                    new CashRegisterClosingResponse(
                        detailRs.getObject("session_uuid", UUID.class),
                        detailRs.getString("register_name"),
                        detailRs.getBigDecimal("opening_balance"),
                        detailRs.getBigDecimal("total_cash_in"),
                        detailRs.getBigDecimal("total_cash_out"),
                        detailRs.getBigDecimal("expected_closing_balance"),
                        detailRs.getBigDecimal("actual_closing_balance"),
                        detailRs.getBigDecimal("difference_amount")
                    ),
                closingId
            );

        return new DailyClosingResponse(
            rs.getObject("uuid", UUID.class),
            rs.getObject("business_date", LocalDate.class),
            rs.getObject("closed_by", Long.class),
            rs.getObject("closed_at", java.time.OffsetDateTime.class),
            rs.getString("notes"),
            summary,
            registers
        );
    }

    private BigDecimal money(ResultSet rs, String column)
            throws SQLException {

        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO;
    }

    private record PaymentTotals(
    	    BigDecimal total,
    	    BigDecimal cash,
    	    BigDecimal upi,
    	    BigDecimal card,
    	    BigDecimal bank,
    	    BigDecimal credit
    	) {}

    	private final RowMapper<PaymentTotals> paymentTotalsMapper =
    	    (rs, rowNum) -> new PaymentTotals(
    	        money(rs, "total_amount"),
    	        money(rs, "cash_total"),
    	        money(rs, "upi_total"),
    	        money(rs, "card_total"),
    	        money(rs, "bank_total"),
    	        money(rs, "credit_total")
    	    );

    private record ReturnTotals(
        Long count,
        BigDecimal total
    ) {}

    private record PurchaseTotals(
        Long count,
        BigDecimal total
    ) {}

    private record ExpenseTotals(
        Long count,
        BigDecimal total,
        BigDecimal cash,
        BigDecimal upi,
        BigDecimal card,
        BigDecimal bank
    ) {}

    private record CashTotals(
        Long count,
        BigDecimal opening,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal expected,
        BigDecimal actual,
        BigDecimal difference
    ) {}
}
