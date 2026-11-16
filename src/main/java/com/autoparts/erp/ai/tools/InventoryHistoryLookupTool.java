package com.autoparts.erp.ai.tools;

import java.time.LocalDate;
import java.time.ZoneId;
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

@Component
public class InventoryHistoryLookupTool {

    private static final Logger log = LoggerFactory.getLogger(InventoryHistoryLookupTool.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public InventoryHistoryLookupTool(
            @Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "Retrieve the latest 50 inventory movements for an exact product code.")
    public List<Map<String, Object>> getProductMovementHistory(String productCode) {
        checkCancellation();
        if (!StringUtils.hasText(productCode)) return List.of();
        return query("getProductMovementHistory", """
                SELECT im.uuid AS movement_uuid, p.product_code, p.part_no,
                       p.name AS product_name, im.movement_type, im.reference_type,
                       im.reference_id, ia.uuid AS adjustment_uuid,
                       ia.reason AS adjustment_reason, ia.notes AS adjustment_notes,
                       im.quantity_change, im.quantity_before, im.quantity_after,
                       im.notes AS movement_notes, im.created_at
                FROM inventory_movements im
                JOIN products p ON p.id = im.product_id
                LEFT JOIN inventory_adjustments ia
                  ON im.reference_type = 'ADJUSTMENT' AND im.reference_id = ia.id
                WHERE LOWER(p.product_code) = LOWER(?)
                   OR LOWER(p.part_no) = LOWER(?)
                ORDER BY im.created_at DESC, im.id DESC
                LIMIT 50
                """, productCode.trim(), productCode.trim());
    }

    @Tool(description = "Summarize inventory movements grouped by movement type for a product code or part number.")
    public List<Map<String, Object>> getProductMovementSummary(String productIdentifier) {
        checkCancellation();
        if (!StringUtils.hasText(productIdentifier)) return List.of();
        return query("getProductMovementSummary", """
                SELECT p.product_code, p.part_no, p.name AS product_name,
                       p.qty AS current_stock, p.unit,
                       im.movement_type,
                       COUNT(*) AS movement_count,
                       SUM(ABS(im.quantity_change)) AS total_quantity_moved,
                       SUM(im.quantity_change) AS net_quantity_change,
                       MIN(im.created_at) AS first_movement_at,
                       MAX(im.created_at) AS latest_movement_at
                FROM inventory_movements im
                JOIN products p ON p.id = im.product_id
                WHERE LOWER(p.product_code) = LOWER(?)
                   OR LOWER(p.part_no) = LOWER(?)
                GROUP BY p.id, p.product_code, p.part_no, p.name, p.qty, p.unit, im.movement_type
                ORDER BY im.movement_type
                """, productIdentifier.trim(), productIdentifier.trim());
    }

    @Tool(description = "Retrieve a product's inventory movements between inclusive YYYY-MM-DD dates.")
    public List<Map<String, Object>> getProductMovementsByDateRange(
            String productIdentifier, String startDate, String endDate) {
        checkCancellation();
        if (!StringUtils.hasText(productIdentifier)
                || !StringUtils.hasText(startDate) || !StringUtils.hasText(endDate)) {
            throw new IllegalArgumentException("Product identifier, start date and end date are required.");
        }

        LocalDate from = parseDate(startDate);
        LocalDate to = parseDate(endDate);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        return query("getProductMovementsByDateRange", """
                SELECT im.uuid AS movement_uuid, p.product_code, p.part_no,
                       p.name AS product_name, im.movement_type, im.reference_type,
                       im.reference_id, ia.reason AS adjustment_reason,
                       ia.notes AS adjustment_notes, im.quantity_change,
                       im.quantity_before, im.quantity_after,
                       im.notes AS movement_notes, im.created_at
                FROM inventory_movements im
                JOIN products p ON p.id = im.product_id
                LEFT JOIN inventory_adjustments ia
                  ON im.reference_type = 'ADJUSTMENT' AND im.reference_id = ia.id
                WHERE (LOWER(p.product_code) = LOWER(?) OR LOWER(p.part_no) = LOWER(?))
                  AND im.created_at >= ?
                  AND im.created_at < ?
                ORDER BY im.created_at DESC, im.id DESC
                LIMIT 100
                """,
                productIdentifier.trim(), productIdentifier.trim(),
                from.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime());
    }

    @Tool(description = "Retrieve recent inventory movements across all products. Limit must be 1 to 50.")
    public List<Map<String, Object>> getRecentInventoryMovements(int requestedLimit) {
        checkCancellation();
        int limit = normalizeLimit(requestedLimit);
        return query("getRecentInventoryMovements", """
                SELECT im.uuid AS movement_uuid, p.product_code, p.part_no,
                       p.name AS product_name, im.movement_type, im.reference_type,
                       im.reference_id, ia.reason AS adjustment_reason,
                       ia.notes AS adjustment_notes, im.quantity_change,
                       im.quantity_before, im.quantity_after,
                       im.notes AS movement_notes, im.created_at
                FROM inventory_movements im
                JOIN products p ON p.id = im.product_id
                LEFT JOIN inventory_adjustments ia
                  ON im.reference_type = 'ADJUSTMENT' AND im.reference_id = ia.id
                ORDER BY im.created_at DESC, im.id DESC
                LIMIT ?
                """, limit);
    }

    public List<Map<String, Object>> getRecentInventoryMovements() {
        return getRecentInventoryMovements(DEFAULT_RECENT_LIMIT);
    }

    @Tool(description = "Retrieve inventory adjustment history. Product identifier is optional; limit must be 1 to 50.")
    public List<Map<String, Object>> getInventoryAdjustments(String productIdentifier, int requestedLimit) {
        checkCancellation();
        int limit = normalizeLimit(requestedLimit);
        boolean filterByProduct = StringUtils.hasText(productIdentifier);
        String sql = """
                SELECT ia.uuid AS adjustment_uuid, ia.reason,
                       ia.notes AS adjustment_notes, ia.created_at AS adjustment_created_at,
                       p.product_code, p.part_no, p.name AS product_name,
                       im.uuid AS movement_uuid, im.quantity_change,
                       im.quantity_before, im.quantity_after,
                       im.created_at AS movement_created_at
                FROM inventory_adjustments ia
                LEFT JOIN inventory_movements im
                  ON im.reference_type = 'ADJUSTMENT' AND im.reference_id = ia.id
                LEFT JOIN products p ON p.id = im.product_id
                """ + (filterByProduct
                ? " WHERE LOWER(p.product_code) = LOWER(?) OR LOWER(p.part_no) = LOWER(?) "
                : "") + """
                ORDER BY ia.created_at DESC, ia.id DESC
                LIMIT ?
                """;
        return filterByProduct
                ? query("getInventoryAdjustments", sql,
                    productIdentifier.trim(), productIdentifier.trim(), limit)
                : query("getInventoryAdjustments", sql, limit);
    }

    public List<Map<String, Object>> getInventoryAdjustments() {
        return getInventoryAdjustments("", MAX_LIMIT);
    }

    private List<Map<String, Object>> query(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve inventory information.", ex);
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD format.", ex);
        }
    }

    private int normalizeLimit(int requested) {
        if (requested < 1) throw new IllegalArgumentException("Limit must be at least 1.");
        return Math.min(requested, MAX_LIMIT);
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI inventory tool execution cancelled");
            throw new CancellationException("AI inventory tool execution was cancelled.");
        }
    }
}
