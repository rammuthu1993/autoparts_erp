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
 * Read-only AI tool for querying Sales Returns, Customer Refunds, Purchase Returns, and Supplier Credits.
 *
 * <p>Granular authorization:
 * <ul>
 *   <li>Customer Sales Returns & Refunds: {@code @SalesAccess} (CASHIER, MANAGER, ADMIN)</li>
 *   <li>Supplier Purchase Returns & Credits: {@code @AdminOrManager} (MANAGER, ADMIN). Cashiers blocked.</li>
 *   <li>Consolidated Return Summaries: {@code @AdminOrManager} (MANAGER, ADMIN). Cashiers blocked.</li>
 * </ul>
 * </p>
 */
@Component
public class ReturnLookupTool {

    private static final Logger log = LoggerFactory.getLogger(ReturnLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public ReturnLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI return tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private void checkAdminOrManagerAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI commercial return tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI return tool."));
    }

    // --------------------------------------------------
    // 1. Search Sales Returns (@SalesAccess)
    // --------------------------------------------------

    @Tool(description = "Search customer sales returns by invoice number or reason.")
    public List<Map<String, Object>> searchSalesReturns(String query) {
        return searchSalesReturns(query, resolveContext());
    }

    public List<Map<String, Object>> searchSalesReturns(String query, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String searchPattern = "%" + query.trim() + "%";
        String sql = """
                SELECT sr.id, sr.return_date, s.invoice_number,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       sr.reason, sr.status, sr.subtotal, sr.total_tax_amount, sr.total_return_amount
                FROM sales_returns sr
                JOIN sales s ON s.id = sr.sale_id
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(s.invoice_number) LIKE LOWER(?)
                   OR LOWER(sr.reason) LIKE LOWER(?)
                   OR LOWER(COALESCE(c.customer_name, s.customer_name)) LIKE LOWER(?)
                ORDER BY sr.return_date DESC, sr.id DESC
                LIMIT 20
                """;

        return queryForList("searchSalesReturns", sql, searchPattern, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 2. Sales Return Details (@SalesAccess)
    // --------------------------------------------------

    @Tool(description = "Retrieve sales return details including returned line items and refund records by invoice number.")
    public Map<String, Object> getSalesReturnDetails(String invoiceNumber) {
        return getSalesReturnDetails(invoiceNumber, resolveContext());
    }

    public Map<String, Object> getSalesReturnDetails(String invoiceNumber, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(invoiceNumber)) {
            return Collections.emptyMap();
        }

        String identifier = invoiceNumber.trim();
        String headerSql = """
                SELECT sr.id, sr.return_date, s.invoice_number,
                       COALESCE(c.customer_name, s.customer_name) AS customer_name,
                       COALESCE(c.phone, s.customer_phone) AS customer_phone,
                       sr.reason, sr.notes, sr.status, sr.subtotal, sr.discount_amount,
                       sr.taxable_amount, sr.total_tax_amount, sr.total_return_amount, sr.created_at
                FROM sales_returns sr
                JOIN sales s ON s.id = sr.sale_id
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE LOWER(s.invoice_number) = LOWER(?)
                ORDER BY sr.return_date DESC, sr.id DESC
                LIMIT 1
                """;

        List<Map<String, Object>> headers = queryForList("getSalesReturnDetailsHeader", headerSql, identifier);
        if (headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> header = new LinkedHashMap<>(headers.get(0));
        Long returnId = (Long) header.get("id");

        String itemsSql = """
                SELECT srp.id, srp.product_code, srp.product_name, srp.quantity,
                       srp.unit, srp.unit_price, srp.taxable_amount, srp.total_tax_amount, srp.line_total
                FROM sales_return_products srp
                WHERE srp.sales_return_id = ?
                ORDER BY srp.id ASC
                """;
        List<Map<String, Object>> items = queryForList("getSalesReturnDetailsItems", itemsSql, returnId);
        header.put("items", items);

        String refundsSql = """
                SELECT srf.id, srf.payment_method, srf.amount, srf.reference_number, srf.refund_date, srf.notes
                FROM sales_refunds srf
                WHERE srf.sales_return_id = ?
                ORDER BY srf.id ASC
                """;
        List<Map<String, Object>> refunds = queryForList("getSalesReturnDetailsRefunds", refundsSql, returnId);
        header.put("refunds", refunds);

        return header;
    }

    // --------------------------------------------------
    // 3. Search Purchase Returns (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Search supplier purchase returns by return number, purchase number, or supplier name.")
    public List<Map<String, Object>> searchPurchaseReturns(String query) {
        return searchPurchaseReturns(query, resolveContext());
    }

    public List<Map<String, Object>> searchPurchaseReturns(String query, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String searchPattern = "%" + query.trim() + "%";
        String sql = """
                SELECT pr.return_number, p.purchase_number, p.supplier_invoice_no,
                       s.supplier_name, pr.return_date, pr.reason, pr.subtotal,
                       pr.tax_total, pr.total_return_amount
                FROM purchase_returns pr
                JOIN purchases p ON p.id = pr.purchase_id
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(pr.return_number) LIKE LOWER(?)
                   OR LOWER(p.purchase_number) LIKE LOWER(?)
                   OR LOWER(p.supplier_invoice_no) LIKE LOWER(?)
                   OR LOWER(s.supplier_name) LIKE LOWER(?)
                ORDER BY pr.return_date DESC, pr.id DESC
                LIMIT 20
                """;

        return queryForList("searchPurchaseReturns", sql, searchPattern, searchPattern, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 4. Purchase Return Details (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve detailed purchase return information including line items and supplier credit note details.")
    public Map<String, Object> getPurchaseReturnDetails(String returnIdentifier) {
        return getPurchaseReturnDetails(returnIdentifier, resolveContext());
    }

    public Map<String, Object> getPurchaseReturnDetails(String returnIdentifier, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(returnIdentifier)) {
            return Collections.emptyMap();
        }

        String identifier = returnIdentifier.trim();
        String headerSql = """
                SELECT pr.id, pr.return_number, p.purchase_number, p.supplier_invoice_no,
                       s.supplier_name, pr.return_date, pr.reason, pr.notes,
                       pr.subtotal, pr.discount_total, pr.tax_total, pr.total_return_amount, pr.created_at
                FROM purchase_returns pr
                JOIN purchases p ON p.id = pr.purchase_id
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(pr.return_number) = LOWER(?)
                   OR LOWER(p.purchase_number) = LOWER(?)
                   OR LOWER(p.supplier_invoice_no) = LOWER(?)
                ORDER BY pr.return_date DESC, pr.id DESC
                LIMIT 1
                """;

        List<Map<String, Object>> headers = queryForList("getPurchaseReturnDetailsHeader", headerSql, identifier, identifier, identifier);
        if (headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> header = new LinkedHashMap<>(headers.get(0));
        Long returnId = (Long) header.get("id");

        String itemsSql = """
                SELECT prp.id, prp.product_code, prp.product_name, prp.quantity,
                       prp.purchase_price, prp.discount_amount, prp.tax_amount, prp.line_total
                FROM purchase_return_products prp
                WHERE prp.purchase_return_id = ?
                ORDER BY prp.id ASC
                """;
        List<Map<String, Object>> items = queryForList("getPurchaseReturnDetailsItems", itemsSql, returnId);
        header.put("items", items);

        String creditsSql = """
                SELECT sc.id, sc.credit_note_number, sc.credit_date, sc.amount,
                       COALESCE((SELECT SUM(sca.amount) FROM supplier_credit_applications sca WHERE sca.supplier_credit_id = sc.id), 0) AS applied_amount,
                       COALESCE((SELECT SUM(sref.amount) FROM supplier_refunds sref WHERE sref.supplier_credit_id = sc.id), 0) AS refunded_amount
                FROM supplier_credits sc
                WHERE sc.purchase_return_id = ?
                """;
        List<Map<String, Object>> credits = queryForList("getPurchaseReturnDetailsCredits", creditsSql, returnId);
        header.put("credits", credits.isEmpty() ? Collections.emptyMap() : credits.get(0));

        return header;
    }

    // --------------------------------------------------
    // 5. Consolidated Return Summary (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve consolidated summary of sales returns and purchase returns, optionally filtered by date range.")
    public Map<String, Object> getReturnSummary(String startDate, String endDate) {
        return getReturnSummary(startDate, endDate, resolveContext());
    }

    public Map<String, Object> getReturnSummary(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String salesReturnSql = """
                SELECT COUNT(*) AS sales_return_count,
                       COALESCE(SUM(total_return_amount), 0) AS sales_return_amount
                FROM sales_returns
                WHERE (? IS NULL OR return_date::date >= ?)
                  AND (? IS NULL OR return_date::date <= ?)
                """;

        String purchaseReturnSql = """
                SELECT COUNT(*) AS purchase_return_count,
                       COALESCE(SUM(total_return_amount), 0) AS purchase_return_amount
                FROM purchase_returns
                WHERE (? IS NULL OR return_date::date >= ?)
                  AND (? IS NULL OR return_date::date <= ?)
                """;

        List<Map<String, Object>> srRows = queryForList("getSalesReturnSummary", salesReturnSql, from, from, to, to);
        List<Map<String, Object>> prRows = queryForList("getPurchaseReturnSummary", purchaseReturnSql, from, from, to, to);

        Map<String, Object> summary = new LinkedHashMap<>();
        if (!srRows.isEmpty()) {
            summary.putAll(srRows.get(0));
        }
        if (!prRows.isEmpty()) {
            summary.putAll(prRows.get(0));
        }
        return summary;
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI return tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI return tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI return tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve return information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI return tool execution cancelled");
            throw new CancellationException("AI return tool execution was cancelled.");
        }
    }
}
