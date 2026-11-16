package com.autoparts.erp.ai.tools;

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

    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MAX_DETAIL_ROWS = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    
    public PurchaseLookupTool(
            @Qualifier("aiToolNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Search recent purchases by purchase number,
     * supplier invoice number, or supplier name.
     */
    @AdminOrManager
    public List<Map<String, Object>> searchPurchases(
            String searchText,
            int requestedLimit
    ) {
        checkCancellation();

        int limit = Math.max(
                1,
                Math.min(MAX_SEARCH_RESULTS, requestedLimit)
        );

        boolean hasSearchText = StringUtils.hasText(searchText);

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
                """);

        MapSqlParameterSource parameters =
                new MapSqlParameterSource();

        if (hasSearchText) {
            sql.append("""
                    WHERE LOWER(p.purchase_number) = LOWER(:purchaseNumber)
                       OR LOWER(p.supplier_invoice_no) = LOWER(:invoiceNumber)
                       OR s.supplier_name ILIKE :supplierName
                    """);

            String value = searchText.trim();

            parameters.addValue("purchaseNumber", value);
            parameters.addValue("invoiceNumber", value);
            parameters.addValue(
                    "supplierName",
                    "%" + value + "%"
            );
        }

        sql.append("""
                GROUP BY p.id, s.supplier_name
                ORDER BY p.purchase_date DESC, p.created_at DESC
                LIMIT :limit
                """);

        parameters.addValue("limit", limit);

        try {
            return jdbcTemplate.queryForList(
                    sql.toString(),
                    parameters
            );
        } catch (DataAccessException exception) {
            log.error("AI purchase search failed", exception);

            throw new IllegalStateException(
                    "Unable to retrieve purchase records at this time.",
                    exception
            );
        }
    }

    /**
     * Retrieve purchase details using an exact purchase number
     * or supplier invoice number.
     */
    @AdminOrManager
    public List<Map<String, Object>> getPurchaseDetails(
            String identifier
    ) {
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
            return jdbcTemplate.queryForList(
                    sql,
                    parameters
            );
        } catch (DataAccessException exception) {
            log.error("AI purchase detail lookup failed", exception);

            throw new IllegalStateException(
                    "Unable to retrieve purchase details at this time.",
                    exception
            );
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "AI purchase lookup was cancelled."
            );
        }
    }
}