package com.autoparts.erp.ai.tools;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;

/**
 * Read-only AI tool for Cash Registers, Operational Expenses, Daily Closings, and Cash Discrepancies.
 *
 * <p>Granular authorization:
 * <ul>
 *   <li>Cash Register active session & drawer status: {@code @SalesAccess} (CASHIER, MANAGER, ADMIN)</li>
 *   <li>Expenses, Daily Closings, and Cash Discrepancies: {@code @AdminOrManager} (MANAGER, ADMIN). Cashiers blocked.</li>
 * </ul>
 * </p>
 */
@Component
public class FinancialLookupTool {

    private static final Logger log = LoggerFactory.getLogger(FinancialLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public FinancialLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI financial tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private void checkAdminOrManagerAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI commercial financial tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI financial tool."));
    }

    // --------------------------------------------------
    // 1. Open Cash Register Status (@SalesAccess)
    // --------------------------------------------------

    @Tool(description = "Retrieve current active cash register sessions, drawer cash totals, and session details.")
    public List<Map<String, Object>> getOpenCashRegisterStatus() {
        return getOpenCashRegisterStatus(resolveContext());
    }

    public List<Map<String, Object>> getOpenCashRegisterStatus(ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        String sql = """
                SELECT cr.register_code, cr.name AS register_name, crs.id AS session_id,
                       crs.opened_at, u.username AS opened_by, crs.opening_balance,
                       COALESCE(SUM(CASE WHEN crm.movement_type = 'IN' THEN crm.amount
                                         WHEN crm.movement_type = 'OUT' THEN -crm.amount
                                         ELSE 0 END), 0) AS net_cash_movement,
                       crs.opening_balance + COALESCE(SUM(CASE WHEN crm.movement_type = 'IN' THEN crm.amount
                                                              WHEN crm.movement_type = 'OUT' THEN -crm.amount
                                                              ELSE 0 END), 0) AS current_drawer_balance
                FROM cash_registers cr
                JOIN cash_register_sessions crs ON crs.cash_register_id = cr.id AND crs.status = 'OPEN'
                JOIN users u ON u.id = crs.opened_by
                LEFT JOIN cash_register_movements crm ON crm.session_id = crs.id
                GROUP BY cr.register_code, cr.name, crs.id, crs.opened_at, u.username, crs.opening_balance
                ORDER BY cr.register_code ASC
                """;

        return queryForList("getOpenCashRegisterStatus", sql);
    }

    // --------------------------------------------------
    // 2. Expense Summary (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve aggregated operating expenses and breakdown by category and payment method, optionally filtered by date range.")
    public Map<String, Object> getExpenseSummary(String startDate, String endDate) {
        return getExpenseSummary(startDate, endDate, resolveContext());
    }

    public Map<String, Object> getExpenseSummary(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        String totalsSql = """
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(amount), 0) AS total_expense_amount,
                       COALESCE(SUM(CASE WHEN payment_method = 'CASH' THEN amount ELSE 0 END), 0) AS cash_expense,
                       COALESCE(SUM(CASE WHEN payment_method = 'UPI' THEN amount ELSE 0 END), 0) AS upi_expense,
                       COALESCE(SUM(CASE WHEN payment_method = 'CARD' THEN amount ELSE 0 END), 0) AS card_expense,
                       COALESCE(SUM(CASE WHEN payment_method = 'BANK' THEN amount ELSE 0 END), 0) AS bank_expense
                FROM expenses
                WHERE status = 'ACTIVE'
                  AND (? IS NULL OR expense_date >= ?)
                  AND (? IS NULL OR expense_date <= ?)
                """;

        String categoryBreakdownSql = """
                SELECT ec.name AS category_name,
                       COUNT(e.id) AS expense_count,
                       COALESCE(SUM(e.amount), 0) AS category_total
                FROM expenses e
                JOIN expense_categories ec ON ec.id = e.category_id
                WHERE e.status = 'ACTIVE'
                  AND (? IS NULL OR e.expense_date >= ?)
                  AND (? IS NULL OR e.expense_date <= ?)
                GROUP BY ec.name
                ORDER BY category_total DESC
                LIMIT 10
                """;

        List<Map<String, Object>> totalsRows = queryForList("getExpenseSummaryTotals", totalsSql, from, from, to, to);
        List<Map<String, Object>> categories = queryForList("getExpenseSummaryCategories", categoryBreakdownSql, from, from, to, to);

        Map<String, Object> summary = new LinkedHashMap<>(totalsRows.isEmpty() ? Collections.emptyMap() : totalsRows.get(0));
        summary.put("categories", categories);
        return summary;
    }

    // --------------------------------------------------
    // 3. Expenses by Category or Title (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Search detailed operating expense entries by category name or expense title.")
    public List<Map<String, Object>> getExpensesByCategory(String categoryQuery, String startDate, String endDate) {
        return getExpensesByCategory(categoryQuery, startDate, endDate, resolveContext());
    }

    public List<Map<String, Object>> getExpensesByCategory(String categoryQuery, String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        String searchPattern = "%" + (StringUtils.hasText(categoryQuery) ? categoryQuery.trim() : "") + "%";
        String sql = """
                SELECT e.id, e.expense_date, e.title, ec.name AS category_name,
                       e.amount, e.payment_method, e.reference_number, e.status
                FROM expenses e
                JOIN expense_categories ec ON ec.id = e.category_id
                WHERE (LOWER(ec.name) LIKE LOWER(?) OR LOWER(e.title) LIKE LOWER(?))
                  AND (? IS NULL OR e.expense_date >= ?)
                  AND (? IS NULL OR e.expense_date <= ?)
                ORDER BY e.expense_date DESC, e.id DESC
                LIMIT 20
                """;

        return queryForList("getExpensesByCategory", sql, searchPattern, searchPattern, from, from, to, to);
    }

    // --------------------------------------------------
    // 4. Daily Closing Lookup (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve the consolidated daily business closing report for a specific date (YYYY-MM-DD).")
    public Map<String, Object> getDailyClosing(String date) {
        return getDailyClosing(date, resolveContext());
    }

    public Map<String, Object> getDailyClosing(String date, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(date)) {
            return Collections.emptyMap();
        }

        LocalDate closingDate = LocalDate.parse(date.trim());
        String sql = """
                SELECT dc.business_date, dc.closed_at, u.username AS closed_by,
                       dc.sales_count, dc.sales_total, dc.sales_paid_total, dc.sales_balance_total,
                       dc.sales_cash_total, dc.sales_upi_total, dc.sales_card_total, dc.sales_bank_total,
                       dc.sales_return_count, dc.sales_return_total, dc.sales_refund_total,
                       dc.purchase_count, dc.purchase_total,
                       dc.expense_count, dc.expense_total,
                       dc.register_count, dc.cash_opening_total, dc.cash_expected_total,
                       dc.cash_actual_total, dc.cash_difference_total, dc.notes
                FROM daily_closings dc
                JOIN users u ON u.id = dc.closed_by
                WHERE dc.business_date = ?
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getDailyClosing", sql, closingDate);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // 5. Cash Discrepancies (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve closed cash register sessions that resulted in a cash surplus or shortage (difference_amount != 0).")
    public List<Map<String, Object>> getCashDiscrepancies(String startDate, String endDate) {
        return getCashDiscrepancies(startDate, endDate, resolveContext());
    }

    public List<Map<String, Object>> getCashDiscrepancies(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        String sql = """
                SELECT cr.register_code, cr.name AS register_name, crs.id AS session_id,
                       crs.opened_at, crs.closed_at, uo.username AS opened_by, uc.username AS closed_by,
                       crs.opening_balance, crs.expected_closing_balance, crs.actual_closing_balance,
                       crs.difference_amount
                FROM cash_register_sessions crs
                JOIN cash_registers cr ON cr.id = crs.cash_register_id
                JOIN users uo ON uo.id = crs.opened_by
                JOIN users uc ON uc.id = crs.closed_by
                WHERE crs.status = 'CLOSED'
                  AND crs.difference_amount <> 0
                  AND (? IS NULL OR crs.closed_at::date >= ?)
                  AND (? IS NULL OR crs.closed_at::date <= ?)
                ORDER BY crs.closed_at DESC, crs.id DESC
                LIMIT 20
                """;

        return queryForList("getCashDiscrepancies", sql, from, from, to, to);
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI financial tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI financial tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI financial tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve financial information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI financial tool execution cancelled");
            throw new CancellationException("AI financial tool execution was cancelled.");
        }
    }
}
