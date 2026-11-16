package com.autoparts.erp.ai.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.service.AiPerformanceMetrics;

@Component
public class ProductLookupTool {

    private static final Logger log =
            LoggerFactory.getLogger(ProductLookupTool.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AiPerformanceMetrics performanceMetrics;

    public ProductLookupTool(
            @Qualifier("aiToolNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate,
            AiPerformanceMetrics performanceMetrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.performanceMetrics = performanceMetrics;
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
    	
    	checkCancellation();

        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool searchProducts called with empty search text");
            return List.of();
        }

        try {

            String sql = """
                SELECT uuid, product_code, part_no, name, qty, unit
                FROM products
                WHERE active = TRUE
                  AND (
                    LOWER(product_code) LIKE LOWER(:searchPattern)
                    OR LOWER(part_no) LIKE LOWER(:searchPattern)
                    OR LOWER(name) LIKE LOWER(:searchPattern)
                  )
                ORDER BY product_code
                LIMIT 20
                """;

            String searchPattern = "%" + searchText.trim() + "%";

            MapSqlParameterSource parameters =
                    new MapSqlParameterSource()
                            .addValue("searchPattern", searchPattern);
            List<Map<String, Object>> products =
                    performanceMetrics.measureDatabaseQuery(
                            "products.search",
                            () -> jdbcTemplate.queryForList(sql, parameters));

            return products;

        } catch (DataAccessException exception) {

            log.error("AI tool execution failed: searchProducts", exception);

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
        checkCancellation();
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

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String productPlaceholders = IntStream.range(0, values.size())
                .mapToObj(index -> {
                    String name = "product" + index;
                    parameters.addValue(name, values.get(index));
                    return ":" + name;
                })
                .collect(Collectors.joining(","));
        String partNumberPlaceholders = IntStream.range(0, values.size())
                .mapToObj(index -> {
                    String name = "partNumber" + index;
                    parameters.addValue(name, values.get(index));
                    return ":" + name;
                })
                .collect(Collectors.joining(","));
        String sql = """
                SELECT product_code, part_no, name, qty, minimum_stock, unit,
                       purchase_price, selling_price
                FROM products
                WHERE active = TRUE
                  AND (LOWER(product_code) IN (%s) OR LOWER(part_no) IN (%s))
                ORDER BY product_code
                """.formatted(productPlaceholders, partNumberPlaceholders);

        try {
            return performanceMetrics.measureDatabaseQuery(
                    "products.compare",
                    () -> jdbcTemplate.queryForList(sql, parameters));
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
    	checkCancellation();
        try {

            String sql = """
                SELECT COUNT(*)
                FROM products
                WHERE active = TRUE
                """;

            Long count = performanceMetrics.measureDatabaseQuery(
                    "products.count",
                    () -> jdbcTemplate.queryForObject(
                            sql,
                            new MapSqlParameterSource(),
                            Long.class));

            return count;

        } catch (DataAccessException exception) {

            log.error("AI tool execution failed: countProducts", exception);

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
    	checkCancellation();
        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool getProductDetails called with empty search text");
            return List.of();
        }

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
                WHERE LOWER(product_code) = LOWER(:productCode)
                   OR LOWER(part_no) = LOWER(:partNumber)
                LIMIT 2
                """;

            String value = searchText.trim();

            MapSqlParameterSource parameters =
                    new MapSqlParameterSource()
                            .addValue("productCode", value)
                            .addValue("partNumber", value);
            List<Map<String, Object>> products =
                    performanceMetrics.measureDatabaseQuery(
                            "products.details",
                            () -> jdbcTemplate.queryForList(sql, parameters));

            return products;

        } catch (DataAccessException exception) {

            log.error("AI tool execution failed: getProductDetails", exception);

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
    	checkCancellation();
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
                    performanceMetrics.measureDatabaseQuery(
                            "products.lowStock",
                            () -> jdbcTemplate.queryForList(
                                    sql,
                                    new MapSqlParameterSource()));

            return products;

        } catch (DataAccessException exception) {

            log.error("AI tool execution failed: getLowStockProducts", exception);

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
    	checkCancellation();
        if (!StringUtils.hasText(searchText)) {
            log.warn("AI tool getProductStock called with empty search text");
            return List.of();
        }

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
                WHERE LOWER(product_code) = LOWER(:productCode)
                   OR LOWER(part_no) = LOWER(:partNumber)
                LIMIT 2
                """;

            String value = searchText.trim();

            MapSqlParameterSource parameters =
                    new MapSqlParameterSource()
                            .addValue("productCode", value)
                            .addValue("partNumber", value);
            List<Map<String, Object>> products =
                    performanceMetrics.measureDatabaseQuery(
                            "products.stock",
                            () -> jdbcTemplate.queryForList(sql, parameters));

            return products;

        } catch (DataAccessException exception) {

            log.error("AI tool execution failed: getProductStock", exception);

            throw new IllegalStateException(
                    "Unable to retrieve product stock at this time.",
                    exception
            );
        }
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
