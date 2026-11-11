
package com.autoparts.erp.ai.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductLookupTool {

    private static final Logger log =
            LoggerFactory.getLogger(ProductLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public ProductLookupTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool searchProducts called with empty search text");
            return List.of();
        }

        long startTime = System.nanoTime();

        log.info("AI tool execution started: searchProducts");

        try {

            String sql = """
                SELECT id, uuid, product_code, part_no
                FROM products
                WHERE LOWER(product_code) LIKE LOWER(?)
                   OR LOWER(part_no) LIKE LOWER(?)
                ORDER BY product_code
                LIMIT 20
                """;

            String searchPattern = "%" + searchText.trim() + "%";

            List<Map<String, Object>> products =
                    jdbcTemplate.queryForList(
                            sql,
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
    // 2. Count Products
    // --------------------------------------------------

    @Tool(description = """
        Count the total number of active products in the ERP.

        Use this tool when the user asks for the total number
        of active products.

        This counts product records, not inventory quantity.
        """)
    public Long countProducts() {

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
}
