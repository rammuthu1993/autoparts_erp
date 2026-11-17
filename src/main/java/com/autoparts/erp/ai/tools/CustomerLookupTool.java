package com.autoparts.erp.ai.tools;

import java.util.Collections;
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
 * Read-only AI tool for querying Customer records, customer sales history, and outstanding balances.
 *
 * <p>Protected under {@code @SalesAccess} authorization:
 * Allows CASHIER, MANAGER, and ADMIN to lookup customer information and credit balances.</p>
 */
@Component
public class CustomerLookupTool {

    private static final Logger log = LoggerFactory.getLogger(CustomerLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public CustomerLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement (@SalesAccess)
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI customer tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI customer tool."));
    }

    // --------------------------------------------------
    // 1. Search Customers
    // --------------------------------------------------

    @Tool(description = "Search customers by name, phone number, or customer code.")
    public List<Map<String, Object>> searchCustomers(String query) {
        return searchCustomers(query, resolveContext());
    }

    public List<Map<String, Object>> searchCustomers(String query, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String searchPattern = "%" + query.trim() + "%";
        String sql = """
                SELECT c.customer_code, c.customer_name, c.phone, c.city, c.state, c.active
                FROM customers c
                WHERE c.active = TRUE
                  AND (LOWER(c.customer_name) LIKE LOWER(?)
                    OR c.phone LIKE ?
                    OR LOWER(c.customer_code) LIKE LOWER(?))
                ORDER BY c.customer_name ASC
                LIMIT 10
                """;

        return queryForList("searchCustomers", sql, searchPattern, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 2. Customer Details
    // --------------------------------------------------

    @Tool(description = "Retrieve customer profile and contact details by customer code, phone, or exact name.")
    public Map<String, Object> getCustomerDetails(String customerQuery) {
        return getCustomerDetails(customerQuery, resolveContext());
    }

    public Map<String, Object> getCustomerDetails(String customerQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(customerQuery)) {
            return Collections.emptyMap();
        }

        String query = customerQuery.trim();
        String sql = """
                SELECT c.id, c.customer_code, c.customer_name, c.phone, c.email,
                       c.city, c.state, c.pincode, c.gstin, c.active, c.created_at
                FROM customers c
                WHERE LOWER(c.customer_code) = LOWER(?)
                   OR c.phone = ?
                   OR LOWER(c.customer_name) = LOWER(?)
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getCustomerDetails", sql, query, query, query);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // 3. Customer Sales History
    // --------------------------------------------------

    @Tool(description = "Retrieve recent sales invoice history for a customer.")
    public List<Map<String, Object>> getCustomerSalesHistory(String customerQuery) {
        return getCustomerSalesHistory(customerQuery, resolveContext());
    }

    public List<Map<String, Object>> getCustomerSalesHistory(String customerQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(customerQuery)) {
            return List.of();
        }

        String query = customerQuery.trim();
        String sql = """
                SELECT s.invoice_number, s.sale_date, s.status, s.payment_status,
                       s.grand_total, s.paid_amount, s.balance_amount
                FROM sales s
                JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(c.customer_code) = LOWER(?)
                   OR c.phone = ?
                   OR LOWER(c.customer_name) LIKE LOWER(?)
                ORDER BY s.sale_date DESC, s.id DESC
                LIMIT 10
                """;

        return queryForList("getCustomerSalesHistory", sql, query, query, "%" + query + "%");
    }

    // --------------------------------------------------
    // 4. Customer Outstanding Balance & Sales Summary
    // --------------------------------------------------

    @Tool(description = "Retrieve customer sales metrics, total invoiced, paid, and outstanding receivable balance.")
    public Map<String, Object> getCustomerOutstandingBalance(String customerQuery) {
        return getCustomerOutstandingBalance(customerQuery, resolveContext());
    }

    public Map<String, Object> getCustomerOutstandingBalance(String customerQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(customerQuery)) {
            return Collections.emptyMap();
        }

        String query = customerQuery.trim();
        String sql = """
                SELECT c.customer_code, c.customer_name, c.phone,
                       COUNT(s.id) AS total_invoices,
                       COALESCE(SUM(s.grand_total), 0) AS total_invoiced,
                       COALESCE(SUM(s.paid_amount), 0) AS total_paid,
                       COALESCE(SUM(s.balance_amount), 0) AS outstanding_balance
                FROM customers c
                LEFT JOIN sales s ON s.customer_id = c.id AND s.status = 'COMPLETED'
                WHERE LOWER(c.customer_code) = LOWER(?)
                   OR c.phone = ?
                   OR LOWER(c.customer_name) LIKE LOWER(?)
                GROUP BY c.id, c.customer_code, c.customer_name, c.phone
                ORDER BY c.customer_name ASC, c.id ASC
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getCustomerOutstandingBalance", sql, query, query, "%" + query + "%");
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI customer tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI customer tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI customer tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve customer information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI customer tool execution cancelled");
            throw new CancellationException("AI customer tool execution was cancelled.");
        }
    }
}
