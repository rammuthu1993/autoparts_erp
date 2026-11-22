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
import com.autoparts.erp.common.exception.ClientValidationException;

/**
 * Read-only AI tool for Executive KPIs, Inventory ABC/XYZ analytics, Customer RFM cohorts,
 * and Cross-Ledger Reconciliation Discrepancies.
 *
 * <p>Granular authorization:
 * <ul>
 *   <li>Standard KPI summary & cohort analytics: {@code @AdminOrManager} (MANAGER, ADMIN). Cashiers blocked.</li>
 *   <li>Reconciliation discrepancies & data quality audits: {@code @AdminOnly} (ADMIN). Managers and Cashiers blocked.</li>
 *   <li>COGS / Profitability Explanation: {@code @SalesAccess} (CASHIER, MANAGER, ADMIN).</li>
 * </ul>
 * </p>
 */
@Component
public class ReportLookupTool {

    private static final Logger log = LoggerFactory.getLogger(ReportLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public ReportLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI report tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private void checkAdminOrManagerAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI management report tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER");
    }

    private void checkAdminOnlyAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI admin reconciliation tool.");
        }
        context.requireAnyRole("ROLE_ADMIN");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI report tool."));
    }

    // --------------------------------------------------
    // 1. Executive KPI Summary (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve high-level executive KPI metrics including sales revenue, purchases, expenses, and net cash flow.")
    public Map<String, Object> getKpiSummary(String startDate, String endDate) {
        return getKpiSummary(startDate, endDate, resolveContext());
    }

    public Map<String, Object> getKpiSummary(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String salesSql = """
                SELECT COUNT(*) AS completed_sales_count,
                       COALESCE(SUM(grand_total), 0) AS total_revenue,
                       COALESCE(SUM(paid_amount), 0) AS collected_revenue,
                       COALESCE(SUM(balance_amount), 0) AS outstanding_receivables
                FROM sales
                WHERE status = 'COMPLETED'
                  AND (? IS NULL OR sale_date >= ?)
                  AND (? IS NULL OR sale_date <= ?)
                """;

        String purchasesSql = """
                SELECT COUNT(*) AS received_purchases_count,
                       COALESCE(SUM(grand_total), 0) AS total_procurement_cost
                FROM purchases
                WHERE status = 'RECEIVED'
                  AND (? IS NULL OR purchase_date >= ?)
                  AND (? IS NULL OR purchase_date <= ?)
                """;

        String expensesSql = """
                SELECT COUNT(*) AS total_expenses_count,
                       COALESCE(SUM(amount), 0) AS total_operating_expenses
                FROM expenses
                WHERE status = 'ACTIVE'
                  AND (? IS NULL OR expense_date >= ?)
                  AND (? IS NULL OR expense_date <= ?)
                """;

        String returnsSql = """
                SELECT COUNT(*) AS sales_returns_count,
                       COALESCE(SUM(total_return_amount), 0) AS total_returned_amount
                FROM sales_returns
                WHERE (? IS NULL OR return_date::date >= ?)
                  AND (? IS NULL OR return_date::date <= ?)
                """;

        List<Map<String, Object>> salesRows = queryForList("getKpiSales", salesSql, from, from, to, to);
        List<Map<String, Object>> purchaseRows = queryForList("getKpiPurchases", purchasesSql, from, from, to, to);
        List<Map<String, Object>> expenseRows = queryForList("getKpiExpenses", expensesSql, from, from, to, to);
        List<Map<String, Object>> returnRows = queryForList("getKpiReturns", returnsSql, from, from, to, to);

        Map<String, Object> kpi = new LinkedHashMap<>();
        if (!salesRows.isEmpty()) kpi.putAll(salesRows.get(0));
        if (!purchaseRows.isEmpty()) kpi.putAll(purchaseRows.get(0));
        if (!expenseRows.isEmpty()) kpi.putAll(expenseRows.get(0));
        if (!returnRows.isEmpty()) kpi.putAll(returnRows.get(0));

        Number collected = (Number) kpi.getOrDefault("collected_revenue", 0);
        Number expenses = (Number) kpi.getOrDefault("total_operating_expenses", 0);
        double netCashInflow = (collected != null ? collected.doubleValue() : 0.0) - (expenses != null ? expenses.doubleValue() : 0.0);
        kpi.put("estimated_net_cash_inflow", netCashInflow);
        kpi.put("estimated_net_cash_inflow_note", "Calculated as (collected sales revenue - active operating expenses); excludes non-sale cash drawer movements, refunds, and direct purchase disbursements.");

        return kpi;
    }

    // --------------------------------------------------
    // 2. Inventory ABC Analysis (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve top revenue-generating products (Category A items contributing the top 80% sales revenue).")
    public List<Map<String, Object>> getInventoryAbcXyzAnalysis() {
        return getInventoryAbcXyzAnalysis(resolveContext());
    }

    public List<Map<String, Object>> getInventoryAbcXyzAnalysis(ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        String sql = """
                WITH product_revenue AS (
                    SELECT p.id AS product_id, p.product_code, p.name AS product_name, p.qty AS current_stock, p.minimum_stock,
                           COALESCE(SUM(sp.line_total), 0) AS revenue,
                           COALESCE(SUM(sp.quantity), 0) AS units_sold
                    FROM products p
                    JOIN sales_products sp ON sp.product_id = p.id
                    JOIN sales s ON s.id = sp.sale_id AND s.status = 'COMPLETED'
                    WHERE p.active = TRUE
                    GROUP BY p.id, p.product_code, p.name, p.qty, p.minimum_stock
                ),
                ranked AS (
                    SELECT product_id, product_code, product_name, current_stock, minimum_stock,
                           revenue, units_sold,
                           SUM(revenue) OVER (ORDER BY revenue DESC, product_id) AS cumulative_revenue,
                           SUM(revenue) OVER () AS total_revenue
                    FROM product_revenue
                )
                SELECT product_code, product_name, current_stock, minimum_stock,
                       revenue AS total_sales_revenue, units_sold AS total_units_sold,
                       CASE WHEN total_revenue = 0 THEN 'C'
                            WHEN cumulative_revenue / total_revenue <= 0.80 THEN 'A'
                            WHEN cumulative_revenue / total_revenue <= 0.95 THEN 'B'
                            ELSE 'C' END AS abc_class,
                       ROUND((cumulative_revenue / NULLIF(total_revenue, 0)) * 100, 2) AS cumulative_revenue_percentage
                FROM ranked
                ORDER BY total_sales_revenue DESC
                LIMIT 20
                """;

        return queryForList("getInventoryAbcXyzAnalysis", sql);
    }

    // --------------------------------------------------
    // 3. Customer RFM Metrics (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve customer cohort metrics based on monetary contribution, invoice frequency, and recent purchase activity.")
    public List<Map<String, Object>> getCustomerRfmAnalysis() {
        return getCustomerRfmAnalysis(resolveContext());
    }

    public List<Map<String, Object>> getCustomerRfmAnalysis(ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        String sql = """
                SELECT c.customer_code, c.customer_name, c.phone,
                       COUNT(s.id) AS total_invoices,
                       COALESCE(SUM(s.grand_total), 0) AS total_spend,
                       MAX(s.sale_date) AS last_purchase_date,
                       CASE
                           WHEN COUNT(s.id) >= 5 AND SUM(s.grand_total) >= 50000 THEN 'VIP / Champion'
                           WHEN COUNT(s.id) >= 3 THEN 'Regular / Loyal'
                           WHEN MAX(s.sale_date) < CURRENT_DATE - INTERVAL '60 days' THEN 'At Risk / Inactive'
                           ELSE 'Promising'
                       END AS rfm_segment
                FROM customers c
                JOIN sales s ON s.customer_id = c.id AND s.status = 'COMPLETED'
                GROUP BY c.id, c.customer_code, c.customer_name, c.phone
                ORDER BY total_spend DESC
                LIMIT 15
                """;

        return queryForList("getCustomerRfmAnalysis", sql);
    }

    // --------------------------------------------------
    // 4. Reconciliation Discrepancies (@AdminOnly)
    // --------------------------------------------------

    @Tool(description = "Audit cross-ledger reconciliation discrepancies, cash session variances, and invoice balance mismatches.")
    public Map<String, Object> getReconciliationDiscrepancies(String startDate, String endDate) {
        return getReconciliationDiscrepancies(startDate, endDate, resolveContext());
    }

    public Map<String, Object> getReconciliationDiscrepancies(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOnlyAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String cashVariancesSql = """
                SELECT cr.register_code, crs.id AS session_id, crs.closed_at,
                       crs.expected_closing_balance, crs.actual_closing_balance, crs.difference_amount
                FROM cash_register_sessions crs
                JOIN cash_registers cr ON cr.id = crs.cash_register_id
                WHERE crs.status = 'CLOSED'
                  AND crs.difference_amount <> 0
                  AND (? IS NULL OR crs.closed_at::date >= ?)
                  AND (? IS NULL OR crs.closed_at::date <= ?)
                ORDER BY crs.closed_at DESC
                LIMIT 10
                """;

        String invoiceMismatchesSql = """
                SELECT s.invoice_number, s.grand_total, s.paid_amount, s.balance_amount,
                       (s.grand_total - (s.paid_amount + s.balance_amount)) AS discrepancy
                FROM sales s
                WHERE s.status = 'COMPLETED'
                  AND ABS(s.grand_total - (s.paid_amount + s.balance_amount)) > 0.01
                  AND (? IS NULL OR s.sale_date >= ?)
                  AND (? IS NULL OR s.sale_date <= ?)
                ORDER BY s.sale_date DESC
                LIMIT 10
                """;

        List<Map<String, Object>> cashVariances = queryForList("getCashVariances", cashVariancesSql, from, from, to, to);
        List<Map<String, Object>> invoiceMismatches = queryForList("getInvoiceMismatches", invoiceMismatchesSql, from, from, to, to);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cash_variances_count", cashVariances.size());
        result.put("cash_variances", cashVariances);
        result.put("invoice_mismatches_count", invoiceMismatches.size());
        result.put("invoice_mismatches", invoiceMismatches);
        return result;
    }

    // --------------------------------------------------
    // 5. Historical COGS & Profit Limitation Explanation (@SalesAccess)
    // --------------------------------------------------

    @Tool(description = "Explain the system limitation regarding historical Cost of Goods Sold (COGS) and gross profit simulation.")
    public Map<String, Object> explainCogsLimitation() {
        return explainCogsLimitation(resolveContext());
    }

    public Map<String, Object> explainCogsLimitation(ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        return Map.of(
                "status", "UNSUPPORTED_METRIC",
                "metric", "Cost of Goods Sold (COGS) & Gross Profit",
                "explanation", "The Auto Parts ERP data model does not record a historical transaction-level Cost of Goods Sold (COGS) basis in line items (`sales_products`). Master `products.purchase_price` is mutable and fluctuates over time as purchase orders are received. Therefore, the system intentionally does not simulate or certify gross margin, gross profit, or product profitability."
        );
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI report tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI report tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI report tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve report metrics.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI report tool execution cancelled");
            throw new CancellationException("AI report tool execution was cancelled.");
        }
    }
}
