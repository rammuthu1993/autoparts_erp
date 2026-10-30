
package com.autoparts.erp.purchase.repository;

import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseProduct;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PurchaseRepositoryImpl implements PurchaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public PurchaseRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean supplierExistsAndActive(Long supplierId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM suppliers
                WHERE id = ? AND active = TRUE
            )
            """,
            Boolean.class,
            supplierId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean productExistsAndActive(Long productId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM products
                WHERE id = ? AND active = TRUE
            )
            """,
            Boolean.class,
            productId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsBySupplierInvoiceNo(
        Long supplierId,
        String invoiceNo
    ) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM purchases
                WHERE supplier_id = ?
                  AND LOWER(TRIM(supplier_invoice_no))
                      = LOWER(TRIM(?))
            )
            """,
            Boolean.class,
            supplierId,
            invoiceNo
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public String nextPurchaseNumber() {
        Long number = jdbcTemplate.queryForObject(
            "SELECT nextval('purchase_number_seq')",
            Long.class
        );

        return "PUR-%08d".formatted(number);
    }

    @Override
    public Purchase createPurchase(
        UUID uuid,
        String purchaseNumber,
        Long supplierId,
        String supplierInvoiceNo,
        LocalDate purchaseDate,
        String notes
    ) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO purchases (
                uuid, purchase_number, supplier_id,
                supplier_invoice_no, purchase_date, notes
            )
            VALUES (?, ?, ?, TRIM(?), ?, ?)
            RETURNING id, uuid, purchase_number, supplier_id,
                      supplier_invoice_no, purchase_date, status,
                      subtotal, discount_total, tax_total, grand_total,
                      notes, created_at, updated_at, received_at
            """,
            (rs, rowNum) -> new Purchase(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("purchase_number"),
                rs.getLong("supplier_id"),
                null,
                rs.getString("supplier_invoice_no"),
                rs.getObject("purchase_date", LocalDate.class),
                PurchaseStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("tax_total"),
                rs.getBigDecimal("grand_total"),
                rs.getString("notes"),
                List.of(),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("received_at", OffsetDateTime.class)
            ),
            uuid,
            purchaseNumber,
            supplierId,
            supplierInvoiceNo,
            purchaseDate,
            notes
        );
    }

    @Override
    public PurchaseProduct createPurchaseItem(
        UUID uuid,
        UUID purchaseUuid,
        Long productId,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        BigDecimal discountAmount,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal lineTotal
    ) {
        Long purchaseId = jdbcTemplate.queryForObject(
            "SELECT id FROM purchases WHERE uuid = ?",
            Long.class,
            purchaseUuid
        );

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO purchase_items (
                uuid, purchase_id, product_id, quantity,
                purchase_price, discount_amount, tax_percent,
                tax_amount, line_total
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, uuid, product_id, quantity,
                      purchase_price, discount_amount, tax_percent,
                      tax_amount, line_total
            """,
            (rs, rowNum) -> mapPurchaseItem(rs),
            uuid,
            purchaseId,
            productId,
            quantity,
            purchasePrice,
            discountAmount,
            taxPercent,
            taxAmount,
            lineTotal
        );
    }

    private PurchaseProduct mapPurchaseItem(ResultSet rs)
        throws SQLException {

        Long productId = rs.getLong("product_id");

        List<ProductInfo> products = jdbcTemplate.query(
            """
            SELECT product_code, part_no, name
            FROM products
            WHERE id = ?
            """,
            (productRs, rowNum) -> new ProductInfo(
                productRs.getString("product_code"),
                productRs.getString("part_no"),
                productRs.getString("name")
            ),
            productId
        );

        ProductInfo product = products.getFirst();

        return new PurchaseProduct(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            productId,
            product.productCode(),
            product.partNo(),
            product.name(),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("purchase_price"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("tax_percent"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("line_total")
        );
    }

    private record ProductInfo(
        String productCode,
        String partNo,
        String name
    ) {}

    @Override
    public void updatePurchaseTotals(
        UUID purchaseUuid,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
    ) {
        int updated = jdbcTemplate.update(
            """
            UPDATE purchases
            SET subtotal = ?,
                discount_total = ?,
                tax_total = ?,
                grand_total = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ? AND status = 'DRAFT'
            """,
            subtotal,
            discountTotal,
            taxTotal,
            grandTotal,
            purchaseUuid
        );

        if (updated == 0) {
            throw new IllegalStateException(
                "Purchase totals could not be updated"
            );
        }
    }

    @Override
    public Optional<Purchase> findByUuid(UUID uuid) {
        List<Purchase> purchases = jdbcTemplate.query(
            """
            SELECT p.id, p.uuid, p.purchase_number, p.supplier_id,
                   s.supplier_name, p.supplier_invoice_no,
                   p.purchase_date, p.status, p.subtotal,
                   p.discount_total, p.tax_total, p.grand_total,
                   p.notes, p.created_at, p.updated_at, p.received_at
            FROM purchases p
            JOIN suppliers s ON s.id = p.supplier_id
            WHERE p.uuid = ?
            """,
            this::mapPurchase,
            uuid
        );

        if (purchases.isEmpty()) {
            return Optional.empty();
        }

        Purchase purchase = purchases.getFirst();
        List<PurchaseProduct> products = findProducts(purchase.id());

        return Optional.of(withProducts(purchase, products));
    }

    @Override
    public List<Purchase> findAll() {
        List<Purchase> purchases = jdbcTemplate.query(
            """
            SELECT p.id, p.uuid, p.purchase_number, p.supplier_id,
                   s.supplier_name, p.supplier_invoice_no,
                   p.purchase_date, p.status, p.subtotal,
                   p.discount_total, p.tax_total, p.grand_total,
                   p.notes, p.created_at, p.updated_at, p.received_at
            FROM purchases p
            JOIN suppliers s ON s.id = p.supplier_id
            ORDER BY p.created_at DESC
            """,
            this::mapPurchase
        );

        List<Purchase> result = new ArrayList<>();

        for (Purchase purchase : purchases) {
            result.add(withProducts(
                purchase,
                findProducts(purchase.id())
            ));
        }

        return result;
    }

    private Purchase mapPurchase(
        ResultSet rs,
        int rowNum
    ) throws SQLException {
        return new Purchase(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("purchase_number"),
            rs.getLong("supplier_id"),
            rs.getString("supplier_name"),
            rs.getString("supplier_invoice_no"),
            rs.getObject("purchase_date", LocalDate.class),
            PurchaseStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("tax_total"),
            rs.getBigDecimal("grand_total"),
            rs.getString("notes"),
            List.of(),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("received_at", OffsetDateTime.class)
        );
    }

    private List<PurchaseProduct> findProducts(Long purchaseId) {
        return jdbcTemplate.query(
            """
            SELECT pi.id, pi.uuid, pi.product_id, pi.quantity,
                   pi.purchase_price, pi.discount_amount,
                   pi.tax_percent, pi.tax_amount, pi.line_total,
                   pr.product_code, pr.part_no, pr.name
            FROM purchase_items pi
            JOIN products pr ON pr.id = pi.product_id
            WHERE pi.purchase_id = ?
            ORDER BY pi.id
            """,
            (rs, rowNum) -> new PurchaseProduct(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("product_id"),
                rs.getString("product_code"),
                rs.getString("part_no"),
                rs.getString("name"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_percent"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("line_total")
            ),
            purchaseId
        );
    }

    private Purchase withProducts(
        Purchase purchase,
        List<PurchaseProduct> products
    ) {
        return new Purchase(
            purchase.id(),
            purchase.uuid(),
            purchase.purchaseNumber(),
            purchase.supplierId(),
            purchase.supplierName(),
            purchase.supplierInvoiceNo(),
            purchase.purchaseDate(),
            purchase.status(),
            purchase.subtotal(),
            purchase.discountTotal(),
            purchase.taxTotal(),
            purchase.grandTotal(),
            purchase.notes(),
            products,
            purchase.createdAt(),
            purchase.updatedAt(),
            purchase.receivedAt()
        );
    }
    

	@Override
	public Optional<Purchase> findByUuidForUpdate(UUID uuid) {
	    List<Purchase> purchases = jdbcTemplate.query(
	        """
	        SELECT p.id, p.uuid, p.purchase_number, p.supplier_id,
	               s.supplier_name, p.supplier_invoice_no,
	               p.purchase_date, p.status, p.subtotal,
	               p.discount_total, p.tax_total, p.grand_total,
	               p.notes, p.created_at, p.updated_at, p.received_at
	        FROM purchases p
	        JOIN suppliers s ON s.id = p.supplier_id
	        WHERE p.uuid = ?
	        FOR UPDATE OF p
	        """,
	        this::mapPurchase,
	        uuid
	    );
	
	    if (purchases.isEmpty()) {
	        return Optional.empty();
	    }
	
	    Purchase purchase = purchases.getFirst();
	
	    return Optional.of(
	        withProducts(purchase, findProducts(purchase.id()))
	    );
	}
	

	@Override
	public BigDecimal findProductQtyForUpdate(Long productId) {
	    return jdbcTemplate.queryForObject(
	        """
	        SELECT qty
	        FROM products
	        WHERE id = ?
	          AND active = TRUE
	        FOR UPDATE
	        """,
	        BigDecimal.class,
	        productId
	    );
	}
	
	
	@Override
	public void increaseProductQty(
	    Long productId,
	    BigDecimal quantity
	) {
	    int updated = jdbcTemplate.update(
	        """
	        UPDATE products
	        SET qty = qty + ?,
	            updated_at = CURRENT_TIMESTAMP
	        WHERE id = ?
	          AND active = TRUE
	        """,
	        quantity,
	        productId
	    );
	
	    if (updated != 1) {
	        throw new IllegalStateException(
	            "Product stock could not be updated: " + productId
	        );
	    }
	}
	
	
	@Override
	public void createInventoryMovement(
	    UUID uuid,
	    Long productId,
	    Long purchaseId,
	    BigDecimal quantityChange,
	    BigDecimal quantityBefore,
	    BigDecimal quantityAfter
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
	        VALUES (?, ?, 'PURCHASE_RECEIPT', 'PURCHASE', ?, ?, ?, ?,
	                'Stock received from purchase')
	        """,
	        uuid,
	        productId,
	        purchaseId,
	        quantityChange,
	        quantityBefore,
	        quantityAfter
	    );
	}
	

	@Override
	public boolean markReceived(UUID purchaseUuid) {
	    return jdbcTemplate.update(
	        """
	        UPDATE purchases
	        SET status = 'RECEIVED',
	            received_at = CURRENT_TIMESTAMP,
	            updated_at = CURRENT_TIMESTAMP
	        WHERE uuid = ?
	          AND status = 'DRAFT'
	        """,
	        purchaseUuid
	    ) == 1;
	}


}
