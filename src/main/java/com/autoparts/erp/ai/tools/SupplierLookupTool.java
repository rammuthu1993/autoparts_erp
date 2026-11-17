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
 * Read-only AI tool for querying Supplier directory, contact information, purchase history, and spend summaries.
 *
 * <p>Granular authorization:
 * <ul>
 *   <li>Supplier directory & contact details: {@code @SalesAccess} (CASHIER, MANAGER, ADMIN)</li>
 *   <li>Supplier purchase transactions & procurement totals: {@code @AdminOrManager} (MANAGER, ADMIN).
 *       ROLE_CASHIER is strictly blocked from commercial purchase data.</li>
 * </ul>
 * </p>
 */
@Component
public class SupplierLookupTool {

    private static final Logger log = LoggerFactory.getLogger(SupplierLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public SupplierLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI supplier tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private void checkAdminOrManagerAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI supplier commercial tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI supplier tool."));
    }

    // --------------------------------------------------
    // 1. Search Suppliers (@SalesAccess)
    // --------------------------------------------------

    @Tool(description = "Search active suppliers by supplier name, contact person, phone, or GSTIN.")
    public List<Map<String, Object>> searchSuppliers(String query) {
        return searchSuppliers(query, resolveContext());
    }

    public List<Map<String, Object>> searchSuppliers(String query, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String searchPattern = "%" + query.trim() + "%";
        String sql = """
                SELECT s.supplier_name, s.contact_person, s.phone, s.city, s.state,
                       s.gstin, s.payment_terms_days, s.active
                FROM suppliers s
                WHERE s.active = TRUE
                  AND (LOWER(s.supplier_name) LIKE LOWER(?)
                    OR LOWER(s.contact_person) LIKE LOWER(?)
                    OR s.phone LIKE ?
                    OR LOWER(s.gstin) LIKE LOWER(?))
                ORDER BY s.supplier_name ASC
                LIMIT 10
                """;

        return queryForList("searchSuppliers", sql, searchPattern, searchPattern, searchPattern, searchPattern);
    }

    // --------------------------------------------------
    // 2. Supplier Details (@SalesAccess)
    // --------------------------------------------------

    @Tool(description = "Retrieve supplier profile, contact details, payment terms, and GSTIN by name, phone, or GSTIN.")
    public Map<String, Object> getSupplierDetails(String supplierQuery) {
        return getSupplierDetails(supplierQuery, resolveContext());
    }

    public Map<String, Object> getSupplierDetails(String supplierQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(supplierQuery)) {
            return Collections.emptyMap();
        }

        String query = supplierQuery.trim();
        String sql = """
                SELECT s.id, s.supplier_name, s.contact_person, s.phone, s.email,
                       s.address, s.city, s.state, s.pincode, s.gstin,
                       s.payment_terms_days, s.active, s.created_at
                FROM suppliers s
                WHERE LOWER(s.supplier_name) = LOWER(?)
                   OR s.phone = ?
                   OR LOWER(s.gstin) = LOWER(?)
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getSupplierDetails", sql, query, query, query);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // 3. Supplier Purchase History (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve recent purchase orders from a specific supplier.")
    public List<Map<String, Object>> getSupplierPurchaseHistory(String supplierQuery) {
        return getSupplierPurchaseHistory(supplierQuery, resolveContext());
    }

    public List<Map<String, Object>> getSupplierPurchaseHistory(String supplierQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(supplierQuery)) {
            return List.of();
        }

        String query = supplierQuery.trim();
        String sql = """
                SELECT p.purchase_number, p.supplier_invoice_no, s.supplier_name,
                       p.purchase_date, p.status, p.grand_total
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE LOWER(s.supplier_name) = LOWER(?)
                   OR LOWER(s.supplier_name) LIKE LOWER(?)
                   OR s.phone = ?
                   OR LOWER(s.gstin) = LOWER(?)
                ORDER BY p.purchase_date DESC, p.id DESC
                LIMIT 10
                """;

        return queryForList("getSupplierPurchaseHistory", sql, query, "%" + query + "%", query, query);
    }

    // --------------------------------------------------
    // 4. Supplier Purchase Summary (@AdminOrManager)
    // --------------------------------------------------

    @Tool(description = "Retrieve total procurement metrics and purchase summaries for a supplier.")
    public Map<String, Object> getSupplierPurchaseSummary(String supplierQuery) {
        return getSupplierPurchaseSummary(supplierQuery, resolveContext());
    }

    public Map<String, Object> getSupplierPurchaseSummary(String supplierQuery, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOrManagerAccess(context);

        if (!StringUtils.hasText(supplierQuery)) {
            return Collections.emptyMap();
        }

        String query = supplierQuery.trim();
        String sql = """
                SELECT s.supplier_name,
                       COUNT(p.id) AS total_purchases,
                       COALESCE(SUM(p.grand_total), 0) AS total_purchased,
                       COALESCE(SUM(CASE WHEN p.status = 'RECEIVED' THEN p.grand_total ELSE 0 END), 0) AS total_received_amount,
                       COUNT(CASE WHEN p.status = 'RECEIVED' THEN 1 END) AS received_count,
                       COUNT(CASE WHEN p.status = 'DRAFT' THEN 1 END) AS draft_count,
                       COUNT(CASE WHEN p.status = 'CANCELLED' THEN 1 END) AS cancelled_count
                FROM suppliers s
                LEFT JOIN purchases p ON p.supplier_id = s.id
                WHERE LOWER(s.supplier_name) = LOWER(?)
                   OR LOWER(s.supplier_name) LIKE LOWER(?)
                   OR s.phone = ?
                   OR LOWER(s.gstin) = LOWER(?)
                GROUP BY s.id, s.supplier_name
                ORDER BY s.supplier_name ASC, s.id ASC
                LIMIT 1
                """;

        List<Map<String, Object>> rows = queryForList("getSupplierPurchaseSummary", sql, query, "%" + query + "%", query, query);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI supplier tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI supplier tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI supplier tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve supplier information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI supplier tool execution cancelled");
            throw new CancellationException("AI supplier tool execution was cancelled.");
        }
    }
}
