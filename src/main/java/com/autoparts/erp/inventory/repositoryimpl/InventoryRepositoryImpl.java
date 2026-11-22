package com.autoparts.erp.inventory.repositoryimpl;

import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
import com.autoparts.erp.inventory.model.InventoryMovement;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repository.InventoryRepository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<InventoryStock> stockRowMapper =
            (rs, rowNum) -> mapStock(rs);

    private InventoryStock mapStock(ResultSet rs)
            throws SQLException {

        return new InventoryStock(
                rs.getLong("product_id"),
                rs.getObject("product_uuid", UUID.class),
                rs.getString("product_code"),
                rs.getString("part_no"),
                rs.getString("product_name"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("minimum_stock"),
                rs.getString("unit")
        );
    }

    private static final String STOCK_SELECT = """
            SELECT
                p.id AS product_id,
                p.uuid AS product_uuid,
                p.product_code,
                p.part_no,
                p.name AS product_name,
                p.qty AS quantity,
                p.minimum_stock,
                p.unit
            FROM products p
            WHERE p.active = TRUE
            """;

    @Override
    public Optional<InventoryStock> findStockByUuid(UUID productUuid) {

        return jdbcTemplate.query(
                STOCK_SELECT + " AND p.uuid = :productUuid",
                new MapSqlParameterSource("productUuid", productUuid),
                stockRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<InventoryStock> findStockById(Long productId) {

        return jdbcTemplate.query(
                STOCK_SELECT + " AND p.id = :productId",
                new MapSqlParameterSource("productId", productId),
                stockRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<InventoryStock> findStockByUuidForUpdate(
            UUID productUuid
    ) {

        return jdbcTemplate.query(
                STOCK_SELECT + """
                         AND p.uuid = :productUuid
                         FOR UPDATE
                        """,
                new MapSqlParameterSource("productUuid", productUuid),
                stockRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<InventoryStock> findStockByIdForUpdate(Long productId) {

        return jdbcTemplate.query(
                STOCK_SELECT + """
                         AND p.id = :productId
                         FOR UPDATE
                        """,
                new MapSqlParameterSource("productId", productId),
                stockRowMapper
        ).stream().findFirst();
    }

    private static final Map<String, String> INVENTORY_STOCK_SORT_ALLOWLIST = Map.of(
            "id", "p.id",
            "productName", "p.name",
            "productCode", "p.product_code",
            "partNo", "p.part_no",
            "quantity", "p.qty",
            "minimumStock", "p.minimum_stock"
    );

    @Override
    public List<InventoryStock> findAllStock() {

        return jdbcTemplate.query(
                STOCK_SELECT + " ORDER BY p.name ASC, p.id ASC LIMIT 501",
                new MapSqlParameterSource(),
                stockRowMapper
        );
    }

    @Override
    public List<InventoryStock> findStockPaged(
            int offset,
            int limit,
            String search,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(STOCK_SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyStockSearch(sql, params, search);

        String sortColumn = INVENTORY_STOCK_SORT_ALLOWLIST.getOrDefault(sortBy, "p.name");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"p.id".equals(sortColumn)) {
            sql.append(", p.id ASC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, stockRowMapper);
    }

    @Override
    public long countStockPaged(String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p WHERE p.active = TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyStockSearch(sql, params, search);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public List<InventoryStock> findLowStock() {

        return jdbcTemplate.query(
                STOCK_SELECT + """
                         AND p.qty <= p.minimum_stock
                         ORDER BY p.qty ASC, p.name ASC, p.id ASC
                         LIMIT 501
                        """,
                new MapSqlParameterSource(),
                stockRowMapper
        );
    }

    @Override
    public List<InventoryStock> findLowStockPaged(
            int offset,
            int limit,
            String search,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(STOCK_SELECT).append(" AND p.qty <= p.minimum_stock ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyStockSearch(sql, params, search);

        String sortColumn = INVENTORY_STOCK_SORT_ALLOWLIST.getOrDefault(sortBy, "p.qty");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"p.id".equals(sortColumn)) {
            sql.append(", p.id ASC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, stockRowMapper);
    }

    @Override
    public long countLowStockPaged(String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p WHERE p.active = TRUE AND p.qty <= p.minimum_stock ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyStockSearch(sql, params, search);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyStockSearch(StringBuilder sql, MapSqlParameterSource params, String search) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND (p.name ILIKE :search OR p.product_code ILIKE :search OR p.part_no ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }
    }

    @Override
    public Long createAdjustment(
            UUID uuid,
            String reason,
            String notes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("reason", reason)
                .addValue("notes", notes);

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO inventory_adjustments (
                    uuid,
                    reason,
                    notes
                )
                VALUES (:uuid, :reason, :notes)
                RETURNING id
                """,
                params,
                Long.class
        );
    }

    @Override
    public void updateProductQuantity(
            Long productId,
            BigDecimal quantity
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("quantity", quantity)
                .addValue("productId", productId);

        int updated = jdbcTemplate.update(
                """
                UPDATE products
                SET qty = :quantity,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :productId
                  AND active = TRUE
                """,
                params
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Product quantity could not be updated"
            );
        }
    }

    @Override
    public void createMovement(
            UUID uuid,
            Long productId,
            InventoryMovementType movementType,
            InventoryReferenceType referenceType,
            Long referenceId,
            BigDecimal quantityChange,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String notes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("productId", productId)
                .addValue("movementType", movementType.name())
                .addValue("referenceType", referenceType.name())
                .addValue("referenceId", referenceId)
                .addValue("quantityChange", quantityChange)
                .addValue("quantityBefore", quantityBefore)
                .addValue("quantityAfter", quantityAfter)
                .addValue("notes", notes);

        jdbcTemplate.update(
                """
                INSERT INTO inventory_movements (
                    uuid,
                    product_id,
                    movement_type,
                    reference_type,
                    reference_id,
                    quantity_change,
                    quantity_before,
                    quantity_after,
                    notes
                )
                VALUES (
                    :uuid,
                    :productId,
                    :movementType,
                    :referenceType,
                    :referenceId,
                    :quantityChange,
                    :quantityBefore,
                    :quantityAfter,
                    :notes
                )
                """,
                params
        );
    }

    private final RowMapper<InventoryMovement> movementRowMapper =
            (rs, rowNum) -> mapMovement(rs);

    private InventoryMovement mapMovement(ResultSet rs)
            throws SQLException {

        return new InventoryMovement(
                rs.getObject("movement_uuid", UUID.class),
                rs.getLong("product_id"),
                rs.getObject("product_uuid", UUID.class),
                rs.getString("product_code"),
                rs.getString("product_name"),
                InventoryMovementType.valueOf(
                        rs.getString("movement_type")
                ),
                InventoryReferenceType.valueOf(
                        rs.getString("reference_type")
                ),
                rs.getLong("reference_id"),
                rs.getBigDecimal("quantity_change"),
                rs.getBigDecimal("quantity_before"),
                rs.getBigDecimal("quantity_after"),
                rs.getString("notes"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private static final String MOVEMENT_SELECT = """
            SELECT
                im.uuid AS movement_uuid,
                im.product_id,
                p.uuid AS product_uuid,
                p.product_code,
                p.name AS product_name,
                im.movement_type,
                im.reference_type,
                im.reference_id,
                im.quantity_change,
                im.quantity_before,
                im.quantity_after,
                im.notes,
                im.created_at
            FROM inventory_movements im
            JOIN products p ON p.id = im.product_id
            WHERE (CAST(:productUuid AS UUID) IS NULL OR p.uuid = CAST(:productUuid AS UUID))
              AND (
                    CAST(:movementType AS VARCHAR) IS NULL
                    OR im.movement_type = CAST(:movementType AS VARCHAR)
                  )
              AND (
                    CAST(:fromDate AS DATE) IS NULL
                    OR im.created_at >= CAST(:fromDate AS DATE)
                  )
              AND (
                    CAST(:toDate AS DATE) IS NULL
                    OR im.created_at < CAST(:toDate AS DATE) + INTERVAL '1 day'
                  )
            """;

    @Override
    public List<InventoryMovement> findMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate,
            int limit,
            int offset
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("productUuid", productUuid)
                .addValue("movementType", movementType == null ? null : movementType.name())
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbcTemplate.query(
                MOVEMENT_SELECT + """
                        ORDER BY im.created_at DESC, im.id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                movementRowMapper
        );
    }

    @Override
    public long countMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("productUuid", productUuid)
                .addValue("movementType", movementType == null ? null : movementType.name())
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate);

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements im
                JOIN products p ON p.id = im.product_id
                WHERE (CAST(:productUuid AS UUID) IS NULL OR p.uuid = CAST(:productUuid AS UUID))
                  AND (
                        CAST(:movementType AS VARCHAR) IS NULL
                        OR im.movement_type = CAST(:movementType AS VARCHAR)
                      )
                  AND (
                        CAST(:fromDate AS DATE) IS NULL
                        OR im.created_at >= CAST(:fromDate AS DATE)
                      )
                  AND (
                        CAST(:toDate AS DATE) IS NULL
                        OR im.created_at < CAST(:toDate AS DATE) + INTERVAL '1 day'
                      )
                """,
                params,
                Long.class
        );

        return count == null ? 0L : count;
    }
}
