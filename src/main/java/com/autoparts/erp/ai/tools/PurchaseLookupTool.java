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
 * Read-only AI tool for querying Purchase orders, invoices, items, and procurement totals.
 *
 * <p>Protected under {@code @AdminOrManager} authorization:
 * Purchases disclose supplier costs, wholesale pricing, supplier invoices, and total spend.
 * ROLE_CASHIER access is strictly blocked before any database execution.</p>
 */
@Component
public class PurchaseLookupTool {

    private static final Logger log = LoggerFactory.getLogger(PurchaseLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public PurchaseLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement (@AdminOrManager)
    // --------------------------------------------------

    private void checkAdminOrManagerAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI purchase tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI purchase tool."));
    }

    // --------------------------------------------------
    // 1. Search Purchases
    // --------------------------------------------------

    @Tool(description = "Search purchases by purchase number, supplier invoice number, or supplier name.")
    public List<Map<String, Object>> searchPurchases(String query) {
        return searchPurchases(query, resolveContext());
    }

    public List<Map<String, Object>> searchPurchases(String query, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String searchPattern = "%" + query.trim() + "%";
        String productCodeOrPartNo = query.trim();
        String sql = """
                SELECT p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.purchase_date, p.status, p.subtotal, p.tax_total, p.grand_total
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(p.purchase_number) LIKE LOWER(?)
                   OR LOWER(p.supplier_invoice_no) LIKE LOWER(?)
                   OR LOWER(s.supplier_name) LIKE LOWER(?)
                   OR EXISTS (
                       SELECT 1 FROM purchase_products pp
                       JOIN products pr ON pr.id = pp.product_id
                       WHERE pp.purchase_id = p.id
                         AND (LOWER(pr.product_code) = LOWER(?) OR LOWER(pr.part_no) = LOWER(?))
                   )
                ORDER BY p.purchase_date DESC, p.id DESC
                LIMIT 20
                """;

        return queryForList("searchPurchases", sql, searchPattern, searchPattern, searchPattern, productCodeOrPartNo, productCodeOrPartNo);
    }

    public List<Map<String, Object>> searchPurchases(String query, String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        if (!StringUtils.hasText(startDate) || !StringUtils.hasText(endDate)) {
            return searchPurchases(query, context);
        }

        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(startDate.trim());
            to = LocalDate.parse(endDate.trim());
        } catch (Exception e) {
            log.warn("Invalid date range for purchase search: start={}, end={}", startDate, endDate);
            return searchPurchases(query, context);
        }

        if (to.isBefore(from)) {
            LocalDate temp = from;
            from = to;
            to = temp;
        }

        String searchPattern = "%" + query.trim() + "%";
        String productCodeOrPartNo = query.trim();
        String sql = """
                SELECT p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.purchase_date, p.status, p.subtotal, p.tax_total, p.grand_total
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE (LOWER(p.purchase_number) LIKE LOWER(?)
                   OR LOWER(p.supplier_invoice_no) LIKE LOWER(?)
                   OR LOWER(s.supplier_name) LIKE LOWER(?)
                   OR EXISTS (
                       SELECT 1 FROM purchase_products pp
                       JOIN products pr ON pr.id = pp.product_id
                       WHERE pp.purchase_id = p.id
                         AND (LOWER(pr.product_code) = LOWER(?) OR LOWER(pr.part_no) = LOWER(?))
                   ))
                   AND p.purchase_date >= ? AND p.purchase_date <= ?
                ORDER BY p.purchase_date DESC, p.id DESC
                LIMIT 20
                """;

        return queryForList("searchPurchasesDateFiltered", sql, searchPattern, searchPattern, searchPattern, productCodeOrPartNo, productCodeOrPartNo, from, to);
    }

    // --------------------------------------------------
    // 2. Purchase Details
    // --------------------------------------------------

    @Tool(description = "Retrieve detailed purchase information including line items by purchase number or supplier invoice number.")
    public Map<String, Object> getPurchaseDetails(String purchaseIdentifier) {
        return getPurchaseDetails(purchaseIdentifier, resolveContext());
    }

    public Map<String, Object> getPurchaseDetails(String purchaseIdentifier, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(purchaseIdentifier)) {
            return Collections.emptyMap();
        }

        String identifier = purchaseIdentifier.trim();
        String headerSql = """
                SELECT p.id, p.uuid, p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.purchase_date, p.status, p.subtotal, p.discount_total, p.tax_total,
                       p.grand_total, p.received_at, p.notes, p.created_at
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(p.purchase_number) = LOWER(?)
                   OR LOWER(p.supplier_invoice_no) = LOWER(?)
                LIMIT 1
                """;

        List<Map<String, Object>> headers = queryForList("getPurchaseDetailsHeader", headerSql, identifier, identifier);
        if (headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> header = new LinkedHashMap<>(headers.get(0));
        Long purchaseId = (Long) header.get("id");

        String itemsSql = """
                SELECT pp.id, pr.product_code, pr.part_no, pr.name AS product_name,
                       pp.quantity, pp.purchase_price, pp.discount_amount, pp.tax_percent,
                       pp.tax_amount, pp.line_total
                FROM purchase_products pp
                JOIN products pr ON pr.id = pp.product_id
                WHERE pp.purchase_id = ?
                ORDER BY pp.id ASC
                """;

        List<Map<String, Object>> items = queryForList("getPurchaseDetailsItems", itemsSql, purchaseId);
        header.put("items", items);
        return header;
    }

    // --------------------------------------------------
    // 3. Purchases by Date Range
    // --------------------------------------------------

    @Tool(description = "Retrieve purchases within an inclusive date range (YYYY-MM-DD).")
    public List<Map<String, Object>> getPurchasesByDateRange(String startDate, String endDate) {
        return getPurchasesByDateRange(startDate, endDate, resolveContext());
    }

    public List<Map<String, Object>> getPurchasesByDateRange(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(startDate) || !StringUtils.hasText(endDate)) {
            throw new ClientValidationException("Start date and end date are required.");
        }

        LocalDate from = LocalDate.parse(startDate.trim());
        LocalDate to = LocalDate.parse(endDate.trim());
        if (to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String sql = """
                SELECT p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.purchase_date, p.status, p.grand_total
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.purchase_date >= ? AND p.purchase_date <= ?
                ORDER BY p.purchase_date DESC, p.id DESC
                LIMIT 50
                """;

        return queryForList("getPurchasesByDateRange", sql, from, to);
    }

    // --------------------------------------------------
    // 4. Purchases by Supplier
    // --------------------------------------------------

    @Tool(description = "Retrieve purchases from a specific supplier by name or GSTIN.")
    public List<Map<String, Object>> getPurchasesBySupplier(String supplierQuery) {
        return getPurchasesBySupplier(supplierQuery, resolveContext());
    }

    public List<Map<String, Object>> getPurchasesBySupplier(String supplierQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(supplierQuery)) {
            return List.of();
        }

        String searchPattern = "%" + supplierQuery.trim() + "%";
        String sql = """
                SELECT p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.purchase_date, p.status, p.grand_total
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(s.supplier_name) LIKE LOWER(?)
                   OR LOWER(s.gstin) LIKE LOWER(?)
                ORDER BY p.purchase_date DESC, p.id DESC
                LIMIT 20
                """;

        return queryForList("getPurchasesBySupplier", sql, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 5. Purchase Totals and Summaries
    // --------------------------------------------------

    @Tool(description = "Retrieve summary metrics for purchases, optionally filtered by date range.")
    public Map<String, Object> getPurchaseSummary(String startDate, String endDate) {
        return getPurchaseSummary(startDate, endDate, resolveContext());
    }

    public Map<String, Object> getPurchaseSummary(String startDate, String endDate, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate.trim()) : null;

        if (from != null && to != null && to.isBefore(from)) {
            throw new ClientValidationException("End date must not be before start date.");
        }

        String sql = """
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(grand_total), 0) AS total_amount,
                       COALESCE(SUM(CASE WHEN status = 'RECEIVED' THEN grand_total ELSE 0 END), 0) AS received_amount,
                       COALESCE(SUM(CASE WHEN status = 'DRAFT' THEN grand_total ELSE 0 END), 0) AS draft_amount,
                       COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN grand_total ELSE 0 END), 0) AS cancelled_amount,
                       COUNT(CASE WHEN status = 'RECEIVED' THEN 1 END) AS received_count,
                       COUNT(CASE WHEN status = 'DRAFT' THEN 1 END) AS draft_count,
                       COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_count
                FROM purchases
                WHERE (? IS NULL OR purchase_date >= ?)
                  AND (? IS NULL OR purchase_date <= ?)
                """;

        List<Map<String, Object>> rows = queryForList("getPurchaseSummary", sql, from, from, to, to);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // 6. Purchase Status Lookup
    // --------------------------------------------------

    @Tool(description = "Retrieve the current status of a purchase order by purchase number or supplier invoice.")
    public Map<String, Object> getPurchaseStatus(String purchaseIdentifier) {
        return getPurchaseStatus(purchaseIdentifier, resolveContext());
    }

    public Map<String, Object> getPurchaseStatus(String purchaseIdentifier, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(purchaseIdentifier)) {
            return Collections.emptyMap();
        }

        String identifier = purchaseIdentifier.trim();
        String sql = """
                SELECT p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.status, p.purchase_date, p.received_at, p.grand_total
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(p.purchase_number) = LOWER(?)
                   OR LOWER(p.supplier_invoice_no) = LOWER(?)
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getPurchaseStatus", sql, identifier, identifier);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI purchase tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI purchase tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI purchase tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve purchase information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI purchase tool execution cancelled");
            throw new CancellationException("AI purchase tool execution was cancelled.");
        }
    }
}
