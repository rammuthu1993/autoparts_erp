package com.autoparts.erp.ai.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;

@Component
public class PurchaseLookupTool {

    private static final Logger log =
            LoggerFactory.getLogger(PurchaseLookupTool.class);

    private static final int DEFAULT_SEARCH_RESULTS = 10;
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MAX_DETAIL_ROWS = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseLookupTool(
            @Qualifier("aiToolNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Backward-compatible search signature.
     */
    @AdminOrManager
    public List<Map<String, Object>> searchPurchases(
            String searchText,
            int requestedLimit) {
        return searchPurchases(
                searchText,
                null,
                null,
                null,
                null,
                requestedLimit);
    }

    /**
     * Search purchases using optional identifier, supplier, status and date filters.
     * End date is inclusive from the user's perspective.
     */
    @AdminOrManager
    public List<Map<String, Object>> searchPurchases(
            String searchText,
            String supplierName,
            String status,
            String startDate,
            String endDate,
            int requestedLimit) {

        checkCancellation();

        int limit = normalizeLimit(requestedLimit);

        LocalDate fromDate = parseOptionalDate(startDate, "startDate");
        LocalDate toDate = parseOptionalDate(endDate, "endDate");

        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(
                    "End date must not be before start date.");
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    p.purchase_number,
                    s.supplier_name,
                    p.supplier_invoice_no,
                    p.purchase_date,
                    p.status,
                    p.subtotal,
                    p.discount_total,
                    p.tax_total,
                    p.grand_total,
                    COUNT(pp.id) AS product_count
                FROM purchases p
                JOIN suppliers s
                    ON s.id = p.supplier_id
                LEFT JOIN purchase_products pp
                    ON pp.purchase_id = p.id
                WHERE 1 = 1
                """);

        MapSqlParameterSource parameters = new MapSqlParameterSource();

        if (StringUtils.hasText(searchText)) {
            sql.append("""
                    AND (
                        LOWER(p.purchase_number) = LOWER(:identifier)
                        OR LOWER(p.supplier_invoice_no) = LOWER(:identifier)
                        OR s.supplier_name ILIKE :identifierSupplier
                    )
                    """);
            String value = searchText.trim();
            parameters.addValue("identifier", value);
            parameters.addValue("identifierSupplier", "%" + value + "%");
        }

        if (StringUtils.hasText(supplierName)) {
            sql.append(" AND s.supplier_name ILIKE :supplierName ");
            parameters.addValue("supplierName", "%" + supplierName.trim() + "%");
        }

        if (StringUtils.hasText(status)) {
            sql.append(" AND UPPER(p.status::text) = UPPER(:status) ");
            parameters.addValue("status", status.trim());
        }

        if (fromDate != null) {
            sql.append(" AND p.purchase_date >= :startDate ");
            parameters.addValue("startDate", fromDate);
        }

        if (toDate != null) {
            sql.append(" AND p.purchase_date < :endDateExclusive ");
            parameters.addValue("endDateExclusive", toDate.plusDays(1));
        }

        sql.append("""
                GROUP BY p.id, s.supplier_name
                ORDER BY
                    p.purchase_date DESC,
                    p.created_at DESC,
                    p.id DESC
                LIMIT :limit
                """);

        parameters.addValue("limit", limit);

        try {
            return jdbcTemplate.queryForList(sql.toString(), parameters);
        } catch (DataAccessException exception) {
            log.error("AI purchase search failed", exception);
            throw new IllegalStateException(
                    "Unable to retrieve purchase records at this time.",
                    exception);
        }
    }

    /**
     * Retrieve purchase details using an exact purchase number
     * or supplier invoice number.
     */
    @AdminOrManager
    public List<Map<String, Object>> getPurchaseDetails(String identifier) {
        checkCancellation();

        if (!StringUtils.hasText(identifier)) {
            return List.of();
        }

        String value = identifier.trim();

        String sql = """
                SELECT
                    p.purchase_number,
                    s.supplier_name,
                    p.supplier_invoice_no,
                    p.purchase_date,
                    p.status,
                    p.subtotal,
                    p.discount_total,
                    p.tax_total,
                    p.grand_total,
                    p.notes,
                    pr.product_code,
                    pr.part_no,
                    pr.name AS product_name,
                    pp.quantity,
                    pp.purchase_price,
                    pp.discount_amount,
                    pp.tax_percent,
                    pp.tax_amount,
                    pp.line_total
                FROM purchases p
                JOIN suppliers s
                    ON s.id = p.supplier_id
                LEFT JOIN purchase_products pp
                    ON pp.purchase_id = p.id
                LEFT JOIN products pr
                    ON pr.id = pp.product_id
                WHERE LOWER(p.purchase_number) = LOWER(:purchaseNumber)
                   OR LOWER(p.supplier_invoice_no) = LOWER(:invoiceNumber)
                ORDER BY
                    p.purchase_date DESC,
                    p.purchase_number,
                    pp.id
                LIMIT :limit
                """;

        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("purchaseNumber", value)
                        .addValue("invoiceNumber", value)
                        .addValue("limit", MAX_DETAIL_ROWS);

        try {
            return jdbcTemplate.queryForList(sql, parameters);
        } catch (DataAccessException exception) {
            log.error("AI purchase detail lookup failed", exception);
            throw new IllegalStateException(
                    "Unable to retrieve purchase details at this time.",
                    exception);
        }
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must use YYYY-MM-DD format.",
                    exception);
        }
    }

    private int normalizeLimit(int requestedLimit) {
        int requested = requestedLimit <= 0
                ? DEFAULT_SEARCH_RESULTS
                : requestedLimit;
        return Math.min(MAX_SEARCH_RESULTS, requested);
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "AI purchase lookup was cancelled.");
        }
    }
}
