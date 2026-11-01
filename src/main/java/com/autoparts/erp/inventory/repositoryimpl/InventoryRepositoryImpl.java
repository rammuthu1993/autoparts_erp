package com.autoparts.erp.inventory.repositoryimpl;

import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
import com.autoparts.erp.inventory.model.InventoryMovement;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repository.InventoryRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public InventoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
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
                STOCK_SELECT + " AND p.uuid = ?",
                stockRowMapper,
                productUuid
        ).stream().findFirst();
    }

    @Override
    public Optional<InventoryStock> findStockByUuidForUpdate(
            UUID productUuid
    ) {

        return jdbcTemplate.query(
                STOCK_SELECT + """
                         AND p.uuid = ?
                         FOR UPDATE
                        """,
                stockRowMapper,
                productUuid
        ).stream().findFirst();
    }

    @Override
    public List<InventoryStock> findAllStock() {

        return jdbcTemplate.query(
                STOCK_SELECT + " ORDER BY p.name ASC",
                stockRowMapper
        );
    }

    @Override
    public List<InventoryStock> findLowStock() {

        return jdbcTemplate.query(
                STOCK_SELECT + """
                         AND p.qty <= p.minimum_stock
                         ORDER BY p.qty ASC, p.name ASC
                        """,
                stockRowMapper
        );
    }

    @Override
    public Long createAdjustment(
            UUID uuid,
            String reason,
            String notes
    ) {

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO inventory_adjustments (
                    uuid,
                    reason,
                    notes
                )
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                uuid,
                reason,
                notes
        );
    }

    @Override
    public void updateProductQuantity(
            Long productId,
            BigDecimal quantity
    ) {

        int updated = jdbcTemplate.update(
                """
                UPDATE products
                SET qty = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND active = TRUE
                """,
                quantity,
                productId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Product quantity could not be updated"
            );
        }
    }

    @Override
    public void createAdjustmentMovement(
            UUID uuid,
            Long productId,
            Long adjustmentId,
            BigDecimal quantityChange,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String notes
    ) {

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
                    ?, ?, 'ADJUSTMENT', 'ADJUSTMENT',
                    ?, ?, ?, ?, ?
                )
                """,
                uuid,
                productId,
                adjustmentId,
                quantityChange,
                quantityBefore,
                quantityAfter,
                notes
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
            WHERE (CAST(? AS UUID) IS NULL OR p.uuid = CAST(? AS UUID))
				  AND (
				        CAST(? AS VARCHAR) IS NULL
				        OR im.movement_type = CAST(? AS VARCHAR)
				      )
				  AND (
				        CAST(? AS DATE) IS NULL
				        OR im.created_at >= CAST(? AS DATE)
				      )
				  AND (
				        CAST(? AS DATE) IS NULL
    		 OR im.created_at < CAST(? AS DATE) + INTERVAL '1 day'
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

        return jdbcTemplate.query(
                MOVEMENT_SELECT + """
                        ORDER BY im.created_at DESC, im.id DESC
                        LIMIT ? OFFSET ?
                        """,
                movementRowMapper,
                productUuid,
                productUuid,
                movementType == null ? null : movementType.name(),
                movementType == null ? null : movementType.name(),
                fromDate,
                fromDate,
                toDate,
                toDate,
                limit,
                offset
        );
    }

    @Override
    public long countMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements im
                JOIN products p ON p.id = im.product_id
                WHERE (CAST(? AS UUID) IS NULL OR p.uuid = CAST(? AS UUID))
					  AND (
					        CAST(? AS VARCHAR) IS NULL
					        OR im.movement_type = CAST(? AS VARCHAR)
					      )
					  AND (
					        CAST(? AS DATE) IS NULL
					        OR im.created_at >= CAST(? AS DATE)
					      )
					  AND (
					        CAST(? AS DATE) IS NULL
					        OR im.created_at < CAST(? AS DATE) + INTERVAL '1 day'
			    )
                """,
                Long.class,
                productUuid,
                productUuid,
                movementType == null ? null : movementType.name(),
                movementType == null ? null : movementType.name(),
                fromDate,
                fromDate,
                toDate,
                toDate
        );

        return count == null ? 0L : count;
    }
}