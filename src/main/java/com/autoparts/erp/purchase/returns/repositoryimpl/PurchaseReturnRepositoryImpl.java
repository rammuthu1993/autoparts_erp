package com.autoparts.erp.purchase.returns.repositoryimpl;

import com.autoparts.erp.purchase.returns.model.PurchaseReturn;
import com.autoparts.erp.purchase.returns.model.PurchaseReturnProduct;
import com.autoparts.erp.purchase.returns.model.SupplierCredit;
import com.autoparts.erp.purchase.returns.repository.PurchaseReturnRepository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseReturnRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
            WHERE uuid = :uuid
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            (rs, rowNum) -> new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            )
        ).stream().findFirst();
    }

    @Override
    public Optional<PurchaseInfo> findPurchaseByUuid(UUID uuid) {
        return jdbcTemplate.query("""
            SELECT id, uuid, supplier_id, status, purchase_number, grand_total
            FROM purchases
            WHERE uuid = :uuid
            """,
            new MapSqlParameterSource("uuid", uuid),
            (rs, rowNum) -> new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            )
        ).stream().findFirst();
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
            WHERE pp.purchase_id = :purchaseId
            ORDER BY pp.id
            FOR UPDATE OF pp
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("purchaseId", purchaseId),
            (rs, rowNum) -> new OriginalPurchaseProduct(
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
            )
        );
    }

    @Override
    public BigDecimal getPreviouslyReturnedQuantity(Long purchaseProductId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM purchase_return_products
            WHERE purchase_product_id = :purchaseProductId
            """,
            new MapSqlParameterSource("purchaseProductId", purchaseProductId),
            BigDecimal.class
        );
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
            WHERE purchase_product_id = :purchaseProductId
            """.formatted(amountColumn);

        return jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource("purchaseProductId", purchaseProductId),
            BigDecimal.class
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
            VALUES (
                :uuid, :returnNumber, :purchaseId, :returnDate,
                :reason, :notes, :subtotal, :discountTotal,
                :taxTotal, :totalReturnAmount
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", value.uuid())
                .addValue("returnNumber", value.returnNumber())
                .addValue("purchaseId", value.purchaseId())
                .addValue("returnDate", value.returnDate())
                .addValue("reason", value.reason())
                .addValue("notes", value.notes())
                .addValue("subtotal", value.subtotal())
                .addValue("discountTotal", value.discountTotal())
                .addValue("taxTotal", value.taxTotal())
                .addValue("totalReturnAmount", value.totalReturnAmount());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            returnMapper
        );
    }

    @Override
    public void createReturnProduct(PurchaseReturnProduct product) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", product.uuid())
                .addValue("purchaseReturnId", product.purchaseReturnId())
                .addValue("purchaseProductId", product.purchaseProductId())
                .addValue("productId", product.productId())
                .addValue("productCode", product.productCode())
                .addValue("productName", product.productName())
                .addValue("quantity", product.quantity())
                .addValue("purchasePrice", product.purchasePrice())
                .addValue("discountAmount", product.discountAmount())
                .addValue("taxPercent", product.taxPercent())
                .addValue("taxAmount", product.taxAmount())
                .addValue("lineTotal", product.lineTotal());

        jdbcTemplate.update("""
            INSERT INTO purchase_return_products (
                uuid, purchase_return_id, purchase_product_id,
                product_id, product_code, product_name, quantity,
                purchase_price, discount_amount, tax_percent,
                tax_amount, line_total
            )
            VALUES (
                :uuid, :purchaseReturnId, :purchaseProductId,
                :productId, :productCode, :productName, :quantity,
                :purchasePrice, :discountAmount, :taxPercent,
                :taxAmount, :lineTotal
            )
            """,
            params
        );
    }

    @Override
    public Optional<PurchaseReturn> findReturnByUuid(UUID uuid) {
        return jdbcTemplate.query("""
            SELECT *
            FROM purchase_returns
            WHERE uuid = :uuid
            """,
            new MapSqlParameterSource("uuid", uuid),
            returnMapper
        ).stream().findFirst();
    }

    @Override
    public List<PurchaseReturn> findReturnsByPurchaseId(Long purchaseId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM purchase_returns
            WHERE purchase_id = :purchaseId
            ORDER BY return_date DESC, id DESC
            """,
            new MapSqlParameterSource("purchaseId", purchaseId),
            returnMapper
        );
    }

    @Override
    public List<PurchaseReturnProduct> findReturnProducts(Long returnId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM purchase_return_products
            WHERE purchase_return_id = :returnId
            ORDER BY id
            """,
            new MapSqlParameterSource("returnId", returnId),
            productMapper
        );
    }

    @Override
    public SupplierCredit createSupplierCredit(
            UUID uuid,
            Long purchaseReturnId,
            BigDecimal amount
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("purchaseReturnId", purchaseReturnId)
                .addValue("amount", amount);

        return jdbcTemplate.queryForObject("""
                INSERT INTO supplier_credits (uuid, purchase_return_id, amount)
                VALUES (:uuid, :purchaseReturnId, :amount)
                RETURNING *
                """,
                params,
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
                )
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
            WHERE sc.uuid = :uuid
            FOR UPDATE OF sc
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            creditMapper
        ).stream().findFirst();
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
            WHERE sc.uuid = :creditUuid
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("creditUuid", creditUuid),
            creditMapper
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
            WHERE sc.purchase_return_id = :returnId
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("returnId", returnId),
            creditMapper
        ).stream().findFirst();
    }

    @Override
    public BigDecimal getRefundedAmount(Long creditId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM supplier_refunds
            WHERE supplier_credit_id = :creditId
            """,
            new MapSqlParameterSource("creditId", creditId),
            BigDecimal.class
        );
    }

    @Override
    public BigDecimal getAppliedAmount(Long creditId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM supplier_credit_applications
            WHERE supplier_credit_id = :creditId
            """,
            new MapSqlParameterSource("creditId", creditId),
            BigDecimal.class
        );
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("creditId", creditId)
                .addValue("paymentMethod", paymentMethod)
                .addValue("amount", amount)
                .addValue("referenceNumber", referenceNumber)
                .addValue("notes", notes)
                .addValue("userId", userId);

        jdbcTemplate.update("""
            INSERT INTO supplier_refunds (
                uuid, supplier_credit_id, payment_method,
                amount, reference_number, notes, created_by
            )
            VALUES (
                :uuid, :creditId, :paymentMethod,
                :amount, :referenceNumber, :notes, :userId
            )
            """,
            params
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("creditId", creditId)
                .addValue("purchaseId", purchaseId)
                .addValue("amount", amount)
                .addValue("userId", userId);

        jdbcTemplate.update("""
            INSERT INTO supplier_credit_applications (
                uuid, supplier_credit_id, purchase_id,
                amount, created_by
            )
            VALUES (:uuid, :creditId, :purchaseId, :amount, :userId)
            """,
            params
        );
    }

    @Override
    public BigDecimal getPurchasePaidAmount(Long purchaseId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM purchase_payments
            WHERE purchase_id = :purchaseId
            """,
            new MapSqlParameterSource("purchaseId", purchaseId),
            BigDecimal.class
        );
    }

    @Override
    public BigDecimal getPurchaseCreditAppliedAmount(Long purchaseId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM supplier_credit_applications
            WHERE purchase_id = :purchaseId
            """,
            new MapSqlParameterSource("purchaseId", purchaseId),
            BigDecimal.class
        );
    }

    @Override
    public BigDecimal getProductQuantityForUpdate(Long productId) {
        return jdbcTemplate.queryForObject("""
            SELECT qty
            FROM products
            WHERE id = :productId
            FOR UPDATE
            """,
            new MapSqlParameterSource("productId", productId),
            BigDecimal.class
        );
    }

    @Override
    public void decreaseProductQuantity(
            Long productId,
            BigDecimal quantity
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("quantity", quantity)
                .addValue("productId", productId);

        int updated = jdbcTemplate.update("""
            UPDATE products
            SET qty = qty - :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :productId
              AND qty >= :quantity
            """,
            params
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("productId", productId)
                .addValue("returnId", returnId)
                .addValue("quantityChange", quantityChange)
                .addValue("quantityBefore", quantityBefore)
                .addValue("quantityAfter", quantityAfter);

        jdbcTemplate.update("""
            INSERT INTO inventory_movements (
                uuid, product_id, movement_type, reference_type,
                reference_id, quantity_change, quantity_before,
                quantity_after, notes
            )
            VALUES (
                :uuid, :productId, 'PURCHASE_RETURN', 'PURCHASE_RETURN',
                :returnId, :quantityChange, :quantityBefore,
                :quantityAfter, 'Stock returned to supplier'
            )
            """,
            params
        );
    }
    
    @Override
    public String generateReturnNumber() {
        Long number = jdbcTemplate.queryForObject(
            "SELECT nextval('purchase_return_number_seq')",
            new MapSqlParameterSource(),
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
            WHERE pr.id = :returnId
            """,
            new MapSqlParameterSource("returnId", returnId),
            (rs, rowNum) -> new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            )
        ).stream().findFirst();
    }

    @Override
    public Optional<UUID> findPurchaseUuidById(Long purchaseId) {
        return jdbcTemplate.query("""
            SELECT uuid FROM purchases WHERE id = :purchaseId
            """,
            new MapSqlParameterSource("purchaseId", purchaseId),
            (rs, rowNum) -> rs.getObject("uuid", UUID.class)
        ).stream().findFirst();
    }

    @Override
    public Optional<UUID> findPurchaseProductUuidById(Long purchaseProductId) {
        return jdbcTemplate.query("""
            SELECT uuid FROM purchase_products WHERE id = :purchaseProductId
            """,
            new MapSqlParameterSource("purchaseProductId", purchaseProductId),
            (rs, rowNum) -> rs.getObject("uuid", UUID.class)
        ).stream().findFirst();
    }

    @Override
    public Optional<UUID> findReturnUuidById(Long returnId) {
        return jdbcTemplate.query("""
            SELECT uuid FROM purchase_returns WHERE id = :returnId
            """,
            new MapSqlParameterSource("returnId", returnId),
            (rs, rowNum) -> rs.getObject("uuid", UUID.class)
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
            WHERE id = :purchaseId
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("purchaseId", purchaseId),
            (rs, rowNum) -> new PurchaseInfo(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("supplier_id"),
                rs.getString("status"),
                rs.getString("purchase_number"),
                rs.getBigDecimal("grand_total")
            )
        ).stream().findFirst();
    }
}
