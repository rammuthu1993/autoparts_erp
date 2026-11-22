package com.autoparts.erp.ai.tools;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * Read-only AI tool for querying Sales invoices, items, customer receipts, and sales revenue.
 *
 * <p>Protected under {@code @SalesAccess} authorization:
 * Allows CASHIER, MANAGER, and ADMIN to lookup sales transactions and invoice records.</p>
 */
@Component
public class SalesLookupTool {

    private static final Logger log = LoggerFactory.getLogger(SalesLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public SalesLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement (@SalesAccess)
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI sales tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI sales tool."));
    }

    // --------------------------------------------------
    // 1. Search Sales
    // --------------------------------------------------

    @Tool(description = "Search sales invoices by invoice number, customer name, or customer phone.")
    public List<Map<String, Object>> searchSales(String query) {
        return searchSales(query, resolveContext());
    }

    public List<Map<String, Object>> searchSales(String query, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String searchPattern = "%" + query.trim() + "%";
        String sql = """
                SELECT s.invoice_number,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       s.customer_phone, s.sale_date, s.status, s.payment_status,
                       s.grand_total, s.paid_amount, s.balance_amount
                FROM sales s
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(s.invoice_number) LIKE LOWER(?)
                   OR LOWER(COALESCE(c.customer_name, s.customer_name)) LIKE LOWER(?)
                   OR s.customer_phone LIKE ?
                ORDER BY s.sale_date DESC, s.id DESC
                LIMIT 20
                """;

        return queryForList("searchSales", sql, searchPattern, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 2. Sales Details
    // --------------------------------------------------

    @Tool(description = "Retrieve detailed sales information including line items and payments by invoice number.")
    public Map<String, Object> getSaleDetails(String invoiceNumber) {
        return getSaleDetails(invoiceNumber, resolveContext());
    }

    public Map<String, Object> getSaleDetails(String invoiceNumber, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(invoiceNumber)) {
            return Collections.emptyMap();
        }

        String identifier = invoiceNumber.trim();
        String headerSql = """
                SELECT s.id, s.uuid, s.invoice_number, s.customer_id, c.customer_code,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       COALESCE(c.phone, s.customer_phone) AS customer_phone,
                       s.sale_date, s.status, s.payment_status, s.subtotal,
                       s.discount_amount, s.taxable_amount, s.cgst_amount, s.sgst_amount,
                       s.igst_amount, s.total_tax_amount, s.grand_total, s.paid_amount,
                       s.balance_amount, s.notes, s.created_at
                FROM sales s
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(s.invoice_number) = LOWER(?)
                LIMIT 1
                """;

        List<Map<String, Object>> headers = queryForList("getSaleDetailsHeader", headerSql, identifier);
        if (headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> header = new LinkedHashMap<>(headers.get(0));
        Long saleId = (Long) header.get("id");

        String itemsSql = """
                SELECT sp.id, sp.product_code, sp.product_name, sp.quantity,
                       sp.unit, sp.unit_price, sp.discount_amount, sp.taxable_amount,
                       sp.total_tax_amount, sp.line_total
                FROM sales_products sp
                WHERE sp.sale_id = ?
                ORDER BY sp.id ASC
                """;
        List<Map<String, Object>> items = queryForList("getSaleDetailsItems", itemsSql, saleId);
        header.put("items", items);

        String paymentsSql = """
                SELECT spay.id, spay.payment_method, spay.amount, spay.reference_number,
                       spay.payment_date, spay.notes
                FROM sales_payments spay
                WHERE spay.sale_id = ?
                ORDER BY spay.id ASC
                """;
        List<Map<String, Object>> payments = queryForList("getSaleDetailsPayments", paymentsSql, saleId);
        header.put("payments", payments);

        return header;
    }

    // --------------------------------------------------
    // 3. Sales by Date Range
    // --------------------------------------------------

    @Tool(description = "Retrieve sales invoices within an inclusive date range (YYYY-MM-DD).")
    public List<Map<String, Object>> getSalesByDateRange(String startDate, String endDate) {
        return getSalesByDateRange(startDate, endDate, resolveContext());
    }

    public List<Map<String, Object>> getSalesByDateRange(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(startDate) || !StringUtils.hasText(endDate)) {
            throw new ClientValidationException("Start date and end date are required.");
        }

        LocalDate from = LocalDate.parse(startDate.trim());
        LocalDate to = LocalDate.parse(endDate.trim());
        if (to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String sql = """
                SELECT s.invoice_number,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       s.sale_date, s.status, s.payment_status, s.grand_total,
                       s.paid_amount, s.balance_amount
                FROM sales s
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE s.sale_date >= ? AND s.sale_date <= ?
                ORDER BY s.sale_date DESC, s.id DESC
                LIMIT 50
                """;

        return queryForList("getSalesByDateRange", sql, from, to);
    }

    // --------------------------------------------------
    // 4. Sales by Customer
    // --------------------------------------------------

    @Tool(description = "Retrieve sales for a customer by customer name, customer code, or phone number.")
    public List<Map<String, Object>> getSalesByCustomer(String customerQuery) {
        return getSalesByCustomer(customerQuery, resolveContext());
    }

    public List<Map<String, Object>> getSalesByCustomer(String customerQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(customerQuery)) {
            return List.of();
        }

        String searchPattern = "%" + customerQuery.trim() + "%";
        String sql = """
                SELECT s.invoice_number,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       s.customer_phone, s.sale_date, s.status, s.payment_status,
                       s.grand_total, s.paid_amount, s.balance_amount
                FROM sales s
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(c.customer_name) LIKE LOWER(?)
                   OR LOWER(c.customer_code) LIKE LOWER(?)
                   OR LOWER(s.customer_name) LIKE LOWER(?)
                   OR s.customer_phone LIKE ?
                ORDER BY s.sale_date DESC, s.id DESC
                LIMIT 20
                """;

        return queryForList("getSalesByCustomer", sql, searchPattern, searchPattern, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 5. Sales Totals and Summaries
    // --------------------------------------------------

    @Tool(description = "Retrieve aggregated sales totals and revenue summaries, optionally filtered by date range.")
    public Map<String, Object> getSalesSummary(String startDate, String endDate) {
        return getSalesSummary(startDate, endDate, resolveContext());
    }

    public Map<String, Object> getSalesSummary(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String sql = """
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(grand_total), 0) AS total_amount,
                       COALESCE(SUM(paid_amount), 0) AS total_paid,
                       COALESCE(SUM(balance_amount), 0) AS total_balance,
                       COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_count,
                       COUNT(CASE WHEN status = 'DRAFT' THEN 1 END) AS draft_count,
                       COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_count,
                       COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN grand_total ELSE 0 END), 0) AS completed_amount,
                       COUNT(CASE WHEN payment_status = 'PAID' THEN 1 END) AS paid_invoices_count,
                       COUNT(CASE WHEN payment_status = 'PARTIALLY_PAID' THEN 1 END) AS partially_paid_count,
                       COUNT(CASE WHEN payment_status = 'UNPAID' THEN 1 END) AS unpaid_count
                FROM sales
                WHERE (? IS NULL OR sale_date >= ?)
                  AND (? IS NULL OR sale_date <= ?)
                """;

        List<Map<String, Object>> rows = queryForList("getSalesSummary", sql, from, from, to, to);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // 6. Sales Status Lookup
    // --------------------------------------------------

    @Tool(description = "Retrieve the status and payment details of a sales invoice by invoice number.")
    public Map<String, Object> getSaleStatus(String invoiceNumber) {
        return getSaleStatus(invoiceNumber, resolveContext());
    }

    public Map<String, Object> getSaleStatus(String invoiceNumber, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(invoiceNumber)) {
            return Collections.emptyMap();
        }

        String identifier = invoiceNumber.trim();
        String sql = """
                SELECT s.invoice_number,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       s.status, s.payment_status, s.sale_date, s.grand_total,
                       s.paid_amount, s.balance_amount
                FROM sales s
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(s.invoice_number) = LOWER(?)
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getSaleStatus", sql, identifier);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI sales tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI sales tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI sales tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve sales information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI sales tool execution cancelled");
            throw new CancellationException("AI sales tool execution was cancelled.");
        }
    }
}
