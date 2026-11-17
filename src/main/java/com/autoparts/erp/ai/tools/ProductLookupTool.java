package com.autoparts.erp.ai.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;

@Component
public class ProductLookupTool {

    private static final Logger log =
            LoggerFactory.getLogger(ProductLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public ProductLookupTool(
            @Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement
    // --------------------------------------------------

    private void checkSalesAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI tool.");
        }
        context.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI tool."));
    }

    // --------------------------------------------------
    // 1. Search Products
    // --------------------------------------------------

    @Tool(description = """
        Search for products in the Auto Parts ERP using product code
        or part number.

        Use this tool when the user asks to find a product.

        This tool only reads product information.
        """)
    public List<Map<String, Object>> searchProducts(String searchText) {
        return searchProducts(searchText, resolveContext());
    }

    public List<Map<String, Object>> searchProducts(String searchText, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool searchProducts called with empty search text");
            return List.of();
        }

        long startTime = System.nanoTime();

        log.info("AI tool execution started: searchProducts");

        try {

            String sql = """
                SELECT uuid, product_code, part_no, name, qty, unit
                FROM products
                WHERE active = TRUE
                  AND (
                    LOWER(product_code) LIKE LOWER(?)
                    OR LOWER(part_no) LIKE LOWER(?)
                    OR LOWER(name) LIKE LOWER(?)
                  )
                ORDER BY product_code
                LIMIT 20
                """;

            String searchPattern = "%" + searchText.trim() + "%";

            List<Map<String, Object>> products =
                    jdbcTemplate.queryForList(
                            sql,
                            searchPattern,
                            searchPattern,
                            searchPattern
                    );

            long duration = getDurationMs(startTime);

            log.info(
                    "AI tool execution completed: searchProducts, resultCount={}, durationMs={}",
                    products.size(),
                    duration
            );

            return products;

        } catch (DataAccessException exception) {

            log.error(
                    "AI tool execution failed: searchProducts, durationMs={}",
                    getDurationMs(startTime),
                    exception
            );

            throw new IllegalStateException(
                    "Unable to search products at this time.",
                    exception
            );
        }
    }

    // --------------------------------------------------
    // Product comparison lookup (exact identifiers only)
    // --------------------------------------------------

    public List<Map<String, Object>> getProductsByIdentifiers(List<String> identifiers) {
        return getProductsByIdentifiers(identifiers, resolveContext());
    }

    public List<Map<String, Object>> getProductsByIdentifiers(List<String> identifiers, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (identifiers == null || identifiers.size() < 2 || identifiers.size() > 5) {
            throw new IllegalArgumentException("Compare between 2 and 5 products.");
        }

        List<String> values = identifiers.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        if (values.size() < 2 || values.size() != identifiers.size()) {
            throw new IllegalArgumentException("Product identifiers must be non-empty and unique.");
        }

        String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
        String sql = """
                SELECT product_code, part_no, name, qty, minimum_stock, unit,
                       purchase_price, selling_price
                FROM products
                WHERE active = TRUE
                  AND (LOWER(product_code) IN (%s) OR LOWER(part_no) IN (%s))
                ORDER BY product_code
                """.formatted(placeholders, placeholders);
        List<Object> parameters = new ArrayList<>(values.size() * 2);
        parameters.addAll(values);
        parameters.addAll(values);

        try {
            return jdbcTemplate.queryForList(sql, parameters.toArray());
        } catch (DataAccessException exception) {
            log.error("AI tool execution failed: getProductsByIdentifiers", exception);
            throw new IllegalStateException("Unable to compare products at this time.", exception);
        }
    }

    // --------------------------------------------------
    // 2. Count Products
    // --------------------------------------------------

    @Tool(description = """
        Count the total number of active products in the ERP.

        Use this tool when the user asks for the total number
        of active products.

        This counts product records, not inventory quantity.
        """)
    public Long countProducts() {
        return countProducts(resolveContext());
    }

    public Long countProducts(ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        long startTime = System.nanoTime();

        log.info("AI tool execution started: countProducts");

        try {

            String sql = """
                SELECT COUNT(*)
                FROM products
                WHERE active = TRUE
                """;

            Long count = jdbcTemplate.queryForObject(sql, Long.class);

            long duration = getDurationMs(startTime);

            log.info(
                    "AI tool execution completed: countProducts, result={}, durationMs={}",
                    count,
                    duration
            );

            return count;

        } catch (DataAccessException exception) {

            log.error(
                    "AI tool execution failed: countProducts, durationMs={}",
                    getDurationMs(startTime),
                    exception
            );

            throw new IllegalStateException(
                    "Unable to retrieve the product count at this time.",
                    exception
            );
        }
    }

    // --------------------------------------------------
    // 3. Get Product Details
    // --------------------------------------------------

    @Tool(description = """
        Retrieve detailed information about a product using its
        exact product code or part number.

        Use this when the user asks for details about a specific product.
        """)
    public List<Map<String, Object>> getProductDetails(String searchText) {
        return getProductDetails(searchText, resolveContext());
    }

    public List<Map<String, Object>> getProductDetails(String searchText, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool getProductDetails called with empty search text");
            return List.of();
        }

        long startTime = System.nanoTime();

        log.info("AI tool execution started: getProductDetails");

        try {

            String sql = """
                SELECT
                    uuid,
                    product_code,
                    part_no,
                    name,
                    description,
                    purchase_price,
                    selling_price,
                    qty,
                    minimum_stock,
                    unit,
                    barcode,
                    active
                FROM products
                WHERE LOWER(product_code) = LOWER(?)
                   OR LOWER(part_no) = LOWER(?)
                LIMIT 2
                """;

            String value = searchText.trim();

            List<Map<String, Object>> products =
                    jdbcTemplate.queryForList(sql, value, value);

            long duration = getDurationMs(startTime);

            log.info(
                    "AI tool execution completed: getProductDetails, resultCount={}, durationMs={}",
                    products.size(),
                    duration
            );

            return products;

        } catch (DataAccessException exception) {

            log.error(
                    "AI tool execution failed: getProductDetails, durationMs={}",
                    getDurationMs(startTime),
                    exception
            );

            throw new IllegalStateException(
                    "Unable to retrieve product details at this time.",
                    exception
            );
        }
    }

    // --------------------------------------------------
    // 4. Get Low Stock Products
    // --------------------------------------------------

    @Tool(description = """
        Retrieve active products whose stock quantity is at or below
        their minimum stock threshold.

        Use this when the user asks for low-stock products or products
        that need restocking.
        """)
    public List<Map<String, Object>> getLowStockProducts() {
        return getLowStockProducts(resolveContext());
    }

    public List<Map<String, Object>> getLowStockProducts(ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        long startTime = System.nanoTime();

        log.info("AI tool execution started: getLowStockProducts");

        try {

            String sql = """
                SELECT
                    product_code,
                    part_no,
                    name,
                    qty,
                    minimum_stock,
                    unit
                FROM products
                WHERE active = TRUE
                  AND qty <= minimum_stock
                ORDER BY qty ASC
                LIMIT 50
                """;

            List<Map<String, Object>> products =
                    jdbcTemplate.queryForList(sql);

            long duration = getDurationMs(startTime);

            log.info(
                    "AI tool execution completed: getLowStockProducts, resultCount={}, durationMs={}",
                    products.size(),
                    duration
            );

            return products;

        } catch (DataAccessException exception) {

            log.error(
                    "AI tool execution failed: getLowStockProducts, durationMs={}",
                    getDurationMs(startTime),
                    exception
            );

            throw new IllegalStateException(
                    "Unable to retrieve low-stock products at this time.",
                    exception
            );
        }
    }

    // --------------------------------------------------
    // 5. Get Product Stock
    // --------------------------------------------------

    @Tool(description = """
        Retrieve the current stock quantity of a product using its
        exact product code or part number.

        Use this when the user asks how much stock is available
        for a specific product.
        """)
    public List<Map<String, Object>> getProductStock(String searchText) {
        return getProductStock(searchText, resolveContext());
    }

    public List<Map<String, Object>> getProductStock(String searchText, ErpAiExecutionContext context) {
        checkCancellation();
        checkSalesAccess(context);

        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool getProductStock called with empty search text");
            return List.of();
        }

        long startTime = System.nanoTime();

        log.info("AI tool execution started: getProductStock");

        try {

            String sql = """
                SELECT
                    product_code,
                    part_no,
                    name,
                    qty,
                    minimum_stock,
                    unit
                FROM products
                WHERE LOWER(product_code) = LOWER(?)
                   OR LOWER(part_no) = LOWER(?)
                LIMIT 2
                """;

            String value = searchText.trim();

            List<Map<String, Object>> products =
                    jdbcTemplate.queryForList(sql, value, value);

            long duration = getDurationMs(startTime);

            log.info(
                    "AI tool execution completed: getProductStock, resultCount={}, durationMs={}",
                    products.size(),
                    duration
            );

            return products;

        } catch (DataAccessException exception) {

            log.error(
                    "AI tool execution failed: getProductStock, durationMs={}",
                    getDurationMs(startTime),
                    exception
            );

            throw new IllegalStateException(
                    "Unable to retrieve product stock at this time.",
                    exception
            );
        }
    }

    // --------------------------------------------------
    // Common Execution Timing
    // --------------------------------------------------

    private long getDurationMs(long startTime) {
        return (System.nanoTime() - startTime) / 1_000_000;
    }
    
    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI tool execution cancelled");

            throw new CancellationException(
                    "AI tool execution was cancelled."
            );
        }
    }
    
    
    
}
