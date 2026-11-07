package com.autoparts.erp.purchase.returns.repositoryimpl;

import com.autoparts.erp.purchase.returns.model.PurchaseReturn;
import com.autoparts.erp.purchase.returns.model.PurchaseReturnProduct;
import com.autoparts.erp.purchase.returns.model.SupplierCredit;
import com.autoparts.erp.purchase.returns.repository.PurchaseReturnRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PurchaseReturnRepositoryImpl
        implements PurchaseReturnRepository {

    private final JdbcTemplate jdbcTemplate;

    public PurchaseReturnRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PurchaseReturn> returnMapper =
        (rs, rowNum) -> mapReturn(rs);

    private final RowMapper<PurchaseReturnProduct> productMapper =
        (rs, rowNum) -> mapReturnProduct(rs);

    private final RowMapper<SupplierCredit> creditMapper =
        (rs, rowNum) -> mapCredit(rs);

    private PurchaseReturn mapReturn(ResultSet rs) throws SQLException {
        return new PurchaseReturn(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("return_number"),
            rs.getLong("purchase_id"),
            rs.getObject("return_date", OffsetDateTime.class),
            rs.getString("reason"),
            rs.getString("notes"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("tax_total"),
            rs.getBigDecimal("total_return_amount"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private PurchaseReturnProduct mapReturnProduct(ResultSet rs)
            throws SQLException {
        return new PurchaseReturnProduct(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("purchase_return_id"),
            rs.getLong("purchase_product_id"),
            rs.getLong("product_id"),
            rs.getString("product_code"),
            rs.getString("product_name"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("purchase_price"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("tax_percent"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("line_total")
        );
    }

    private SupplierCredit mapCredit(ResultSet rs)
            throws SQLException {

        return new SupplierCredit(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("purchase_return_id"),
            rs.getString("credit_note_number"),
            rs.getObject("credit_date", OffsetDateTime.class),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("refunded_amount"),
            rs.getBigDecimal("applied_amount"),
            rs.getBigDecimal("available_amount"),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    @Override
    public Optional<PurchaseInfo> findPurchaseByUuidForUpdate(UUID uuid) {
        String sql = """
            SELECT id, uuid, supplier_id, status,
                   purchase_number, grand_total
            FROM purchases
            WHERE uuid = ?
            FOR UPDATE
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
            new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            ), uuid).stream().findFirst();
    }

    @Override
    public Optional<PurchaseInfo> findPurchaseByUuidForUpdateForCredit(
            UUID uuid
    ) {
        return findPurchaseByUuidForUpdate(uuid);
    }

    @Override
    public List<OriginalPurchaseProduct> findPurchaseProducts(
            Long purchaseId
    ) {
        String sql = """
            SELECT
                pp.id,
                pp.uuid,
                pp.product_id,
                p.product_code,
                p.name AS product_name,
                pp.quantity,
                pp.purchase_price,
                pp.discount_amount,
                pp.tax_percent,
                pp.tax_amount,
                pp.line_total
            FROM purchase_products pp
            JOIN products p ON p.id = pp.product_id
            WHERE pp.purchase_id = ?
            ORDER BY pp.id
            FOR UPDATE OF pp
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
            new OriginalPurchaseProduct(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("product_id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_percent"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("line_total")
            ), purchaseId);
    }

    @Override
    public BigDecimal getPreviouslyReturnedQuantity(Long purchaseProductId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM purchase_return_products
            WHERE purchase_product_id = ?
            """, BigDecimal.class, purchaseProductId);
    }

    @Override
    public BigDecimal getPreviouslyReturnedAmount(
            Long purchaseProductId,
            String amountColumn
    ) {
        // Only fixed, internally controlled column names are accepted.
        if (!List.of("discount_amount", "tax_amount", "line_total")
                .contains(amountColumn)) {
            throw new IllegalArgumentException("Invalid amount column");
        }

        String sql = """
            SELECT COALESCE(SUM(%s), 0)
            FROM purchase_return_products
            WHERE purchase_product_id = ?
            """.formatted(amountColumn);

        return jdbcTemplate.queryForObject(
            sql,
            BigDecimal.class,
            purchaseProductId
        );
    }

    @Override
    public PurchaseReturn createReturn(PurchaseReturn value) {
        String sql = """
            INSERT INTO purchase_returns (
                uuid, return_number, purchase_id, return_date,
                reason, notes, subtotal, discount_total,
                tax_total, total_return_amount
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            returnMapper,
            value.uuid(),
            value.returnNumber(),
            value.purchaseId(),
            value.returnDate(),
            value.reason(),
            value.notes(),
            value.subtotal(),
            value.discountTotal(),
            value.taxTotal(),
            value.totalReturnAmount()
        );
    }

    @Override
    public void createReturnProduct(PurchaseReturnProduct product) {
        jdbcTemplate.update("""
            INSERT INTO purchase_return_products (
                uuid, purchase_return_id, purchase_product_id,
                product_id, product_code, product_name, quantity,
                purchase_price, discount_amount, tax_percent,
                tax_amount, line_total
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            product.uuid(),
            product.purchaseReturnId(),
            product.purchaseProductId(),
            product.productId(),
            product.productCode(),
            product.productName(),
            product.quantity(),
            product.purchasePrice(),
            product.discountAmount(),
            product.taxPercent(),
            product.taxAmount(),
            product.lineTotal()
        );
    }

    @Override
    public Optional<PurchaseReturn> findReturnByUuid(UUID uuid) {
        return jdbcTemplate.query("""
            SELECT *
            FROM purchase_returns
            WHERE uuid = ?
            """, returnMapper, uuid).stream().findFirst();
    }

    @Override
    public List<PurchaseReturnProduct> findReturnProducts(Long returnId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM purchase_return_products
            WHERE purchase_return_id = ?
            ORDER BY id
            """, productMapper, returnId);
    }

    @Override
    public SupplierCredit createSupplierCredit(
            UUID uuid,
            Long purchaseReturnId,
            BigDecimal amount
    ) {
    	return jdbcTemplate.queryForObject("""
    		    INSERT INTO supplier_credits (uuid, purchase_return_id, amount)
    		    VALUES (?, ?, ?)
    		    RETURNING *
    		    """,
    		    (rs, rowNum) -> new SupplierCredit(
    		        rs.getLong("id"),
    		        rs.getObject("uuid", UUID.class),
    		        rs.getLong("purchase_return_id"),
    		        rs.getString("credit_note_number"),
    		        rs.getObject("credit_date", OffsetDateTime.class),
    		        rs.getBigDecimal("amount"),
    		        BigDecimal.ZERO,
    		        BigDecimal.ZERO,
    		        rs.getBigDecimal("amount"),
    		        rs.getObject("created_at", OffsetDateTime.class)
    		    ),
    		    uuid,
    		    purchaseReturnId,
    		    amount
    		);
    }

    @Override
    public Optional<SupplierCredit> findCreditByUuidForUpdate(UUID uuid) {
        String sql = """
            SELECT
                sc.*,
                COALESCE((
                    SELECT SUM(sr.amount)
                    FROM supplier_refunds sr
                    WHERE sr.supplier_credit_id = sc.id
                ), 0) AS refunded_amount,
                COALESCE((
                    SELECT SUM(sca.amount)
                    FROM supplier_credit_applications sca
                    WHERE sca.supplier_credit_id = sc.id
                ), 0) AS applied_amount,
                sc.amount
                  - COALESCE((
                      SELECT SUM(sr.amount)
                      FROM supplier_refunds sr
                      WHERE sr.supplier_credit_id = sc.id
                    ), 0)
                  - COALESCE((
                      SELECT SUM(sca.amount)
                      FROM supplier_credit_applications sca
                      WHERE sca.supplier_credit_id = sc.id
                    ), 0) AS available_amount
            FROM supplier_credits sc
            WHERE sc.uuid = ?
            FOR UPDATE OF sc
            """;

        return jdbcTemplate.query(sql, creditMapper, uuid)
            .stream().findFirst();
    }
    
    @Override
    public Optional<SupplierCredit> findCreditByUuid(UUID creditUuid) {

        String sql = """
            SELECT
                sc.*,
                COALESCE((
                    SELECT SUM(sr.amount)
                    FROM supplier_refunds sr
                    WHERE sr.supplier_credit_id = sc.id
                ), 0) AS refunded_amount,
                COALESCE((
                    SELECT SUM(sca.amount)
                    FROM supplier_credit_applications sca
                    WHERE sca.supplier_credit_id = sc.id
                ), 0) AS applied_amount,
                sc.amount
                    - COALESCE((
                        SELECT SUM(sr.amount)
                        FROM supplier_refunds sr
                        WHERE sr.supplier_credit_id = sc.id
                    ), 0)
                    - COALESCE((
                        SELECT SUM(sca.amount)
                        FROM supplier_credit_applications sca
                        WHERE sca.supplier_credit_id = sc.id
                    ), 0) AS available_amount
            FROM supplier_credits sc
            WHERE sc.uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            creditMapper,
            creditUuid
        ).stream().findFirst();
    }

    @Override
    public Optional<SupplierCredit> findCreditByReturnId(Long returnId) {
        String sql = """
            SELECT
                sc.*,
                COALESCE((
                    SELECT SUM(sr.amount)
                    FROM supplier_refunds sr
                    WHERE sr.supplier_credit_id = sc.id
                ), 0) AS refunded_amount,
                COALESCE((
                    SELECT SUM(sca.amount)
                    FROM supplier_credit_applications sca
                    WHERE sca.supplier_credit_id = sc.id
                ), 0) AS applied_amount,
                sc.amount
                  - COALESCE((
                      SELECT SUM(sr.amount)
                      FROM supplier_refunds sr
                      WHERE sr.supplier_credit_id = sc.id
                    ), 0)
                  - COALESCE((
                      SELECT SUM(sca.amount)
                      FROM supplier_credit_applications sca
                      WHERE sca.supplier_credit_id = sc.id
                    ), 0) AS available_amount
            FROM supplier_credits sc
            WHERE sc.purchase_return_id = ?
            """;

        return jdbcTemplate.query(sql, creditMapper, returnId)
            .stream().findFirst();
    }

    @Override
    public BigDecimal getRefundedAmount(Long creditId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM supplier_refunds
            WHERE supplier_credit_id = ?
            """, BigDecimal.class, creditId);
    }

    @Override
    public BigDecimal getAppliedAmount(Long creditId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM supplier_credit_applications
            WHERE supplier_credit_id = ?
            """, BigDecimal.class, creditId);
    }

    @Override
    public void createSupplierRefund(
            UUID uuid,
            Long creditId,
            String paymentMethod,
            BigDecimal amount,
            String referenceNumber,
            String notes,
            Long userId
    ) {
        jdbcTemplate.update("""
            INSERT INTO supplier_refunds (
                uuid, supplier_credit_id, payment_method,
                amount, reference_number, notes, created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            uuid,
            creditId,
            paymentMethod,
            amount,
            referenceNumber,
            notes,
            userId
        );
    }

    @Override
    public void createCreditApplication(
            UUID uuid,
            Long creditId,
            Long purchaseId,
            BigDecimal amount,
            Long userId
    ) {
        jdbcTemplate.update("""
            INSERT INTO supplier_credit_applications (
                uuid, supplier_credit_id, purchase_id,
                amount, created_by
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            uuid,
            creditId,
            purchaseId,
            amount,
            userId
        );
    }

    @Override
    public BigDecimal getPurchasePaidAmount(Long purchaseId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM purchase_payments
            WHERE purchase_id = ?
            """, BigDecimal.class, purchaseId);
    }

    @Override
    public BigDecimal getPurchaseCreditAppliedAmount(Long purchaseId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM supplier_credit_applications
            WHERE purchase_id = ?
            """, BigDecimal.class, purchaseId);
    }

    @Override
    public BigDecimal getProductQuantityForUpdate(Long productId) {
        return jdbcTemplate.queryForObject("""
            SELECT qty
            FROM products
            WHERE id = ?
            FOR UPDATE
            """, BigDecimal.class, productId);
    }

    @Override
    public void decreaseProductQuantity(
            Long productId,
            BigDecimal quantity
    ) {
        int updated = jdbcTemplate.update("""
            UPDATE products
            SET qty = qty - ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND qty >= ?
            """,
            quantity,
            productId,
            quantity
        );

        if (updated != 1) {
            throw new IllegalStateException(
                "Insufficient stock or product not found: " + productId
            );
        }
    }

    @Override
    public void createPurchaseReturnInventoryMovement(
            UUID uuid,
            Long productId,
            Long returnId,
            BigDecimal quantityChange,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter
    ) {
        jdbcTemplate.update("""
            INSERT INTO inventory_movements (
                uuid, product_id, movement_type, reference_type,
                reference_id, quantity_change, quantity_before,
                quantity_after, notes
            )
            VALUES (
                ?, ?, 'PURCHASE_RETURN', 'PURCHASE_RETURN',
                ?, ?, ?, ?, 'Stock returned to supplier'
            )
            """,
            uuid,
            productId,
            returnId,
            quantityChange,
            quantityBefore,
            quantityAfter
        );
    }
    
    @Override
    public String generateReturnNumber() {
        Long number = jdbcTemplate.queryForObject(
            "SELECT nextval('purchase_return_number_seq')",
            Long.class
        );

        return "PR-" + String.format("%06d", number);
    }
    
    @Override
    public Optional<PurchaseInfo> findPurchaseForCreditByReturnId(Long returnId) {
        return jdbcTemplate.query("""
            SELECT p.id, p.uuid, p.supplier_id, p.status,
                   p.purchase_number, p.grand_total
            FROM purchases p
            JOIN purchase_returns pr ON pr.purchase_id = p.id
            WHERE pr.id = ?
            """,
            (rs, rowNum) -> new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            ),
            returnId
        ).stream().findFirst();
    }

    @Override
    public Optional<UUID> findPurchaseUuidById(Long purchaseId) {
        return jdbcTemplate.query("""
            SELECT uuid FROM purchases WHERE id = ?
            """,
            (rs, rowNum) -> rs.getObject("uuid", UUID.class),
            purchaseId
        ).stream().findFirst();
    }

    @Override
    public Optional<UUID> findPurchaseProductUuidById(Long purchaseProductId) {
        return jdbcTemplate.query("""
            SELECT uuid FROM purchase_products WHERE id = ?
            """,
            (rs, rowNum) -> rs.getObject("uuid", UUID.class),
            purchaseProductId
        ).stream().findFirst();
    }

    @Override
    public Optional<UUID> findReturnUuidById(Long returnId) {
        return jdbcTemplate.query("""
            SELECT uuid FROM purchase_returns WHERE id = ?
            """,
            (rs, rowNum) -> rs.getObject("uuid", UUID.class),
            returnId
        ).stream().findFirst();
    }
    
    @Override
    public Optional<PurchaseInfo> findPurchaseById(Long purchaseId) {

        String sql = """
            SELECT
                id,
                uuid,
                supplier_id,
                status,
                purchase_number,
                grand_total
            FROM purchases
            WHERE id = ?
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            ),
            purchaseId
        ).stream().findFirst();
    }
}