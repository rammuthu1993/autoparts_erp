package com.autoparts.erp.purchase.repositoryimpl;

import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseProduct;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import com.autoparts.erp.purchase.repository.PurchaseRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PurchaseRepositoryImpl implements PurchaseRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean supplierExistsAndActive(Long supplierId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM suppliers
                WHERE id = :supplierId AND active = TRUE
            )
            """,
            new MapSqlParameterSource("supplierId", supplierId),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean productExistsAndActive(Long productId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM products
                WHERE id = :productId AND active = TRUE
            )
            """,
            new MapSqlParameterSource("productId", productId),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsBySupplierInvoiceNo(
        Long supplierId,
        String invoiceNo
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("supplierId", supplierId)
                .addValue("invoiceNo", invoiceNo);

        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM purchases
                WHERE supplier_id = :supplierId
                  AND LOWER(TRIM(supplier_invoice_no))
                      = LOWER(TRIM(:invoiceNo))
            )
            """,
            params,
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public String nextPurchaseNumber() {
        Long number = jdbcTemplate.queryForObject(
            "SELECT nextval('purchase_number_seq')",
            new MapSqlParameterSource(),
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("purchaseNumber", purchaseNumber)
                .addValue("supplierId", supplierId)
                .addValue("supplierInvoiceNo", supplierInvoiceNo)
                .addValue("purchaseDate", purchaseDate)
                .addValue("notes", notes);

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO purchases (
                uuid, purchase_number, supplier_id,
                supplier_invoice_no, purchase_date, notes
            )
            VALUES (:uuid, :purchaseNumber, :supplierId, TRIM(:supplierInvoiceNo), :purchaseDate, :notes)
            RETURNING id, uuid, purchase_number, supplier_id,
                      supplier_invoice_no, purchase_date, status,
                      subtotal, discount_total, tax_total, grand_total,
                      notes, created_at, updated_at, received_at
            """,
            params,
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
            )
        );
    }

    @Override
    public PurchaseProduct createPurchaseProduct(
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
            "SELECT id FROM purchases WHERE uuid = :purchaseUuid",
            new MapSqlParameterSource("purchaseUuid", purchaseUuid),
            Long.class
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("purchaseId", purchaseId)
                .addValue("productId", productId)
                .addValue("quantity", quantity)
                .addValue("purchasePrice", purchasePrice)
                .addValue("discountAmount", discountAmount)
                .addValue("taxPercent", taxPercent)
                .addValue("taxAmount", taxAmount)
                .addValue("lineTotal", lineTotal);

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO purchase_products (
                uuid, purchase_id, product_id, quantity,
                purchase_price, discount_amount, tax_percent,
                tax_amount, line_total
            )
            VALUES (
                :uuid, :purchaseId, :productId, :quantity,
                :purchasePrice, :discountAmount, :taxPercent,
                :taxAmount, :lineTotal
            )
            RETURNING id, uuid, product_id, quantity,
                      purchase_price, discount_amount, tax_percent,
                      tax_amount, line_total
            """,
            params,
            (rs, rowNum) -> mapPurchaseProduct(rs)
        );
    }

    private PurchaseProduct mapPurchaseProduct(ResultSet rs)
        throws SQLException {

        Long productId = rs.getLong("product_id");

        List<ProductInfo> products = jdbcTemplate.query(
            """
            SELECT product_code, part_no, name
            FROM products
            WHERE id = :productId
            """,
            new MapSqlParameterSource("productId", productId),
            (productRs, rowNum) -> new ProductInfo(
                productRs.getString("product_code"),
                productRs.getString("part_no"),
                productRs.getString("name")
            )
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subtotal", subtotal)
                .addValue("discountTotal", discountTotal)
                .addValue("taxTotal", taxTotal)
                .addValue("grandTotal", grandTotal)
                .addValue("purchaseUuid", purchaseUuid);

        int updated = jdbcTemplate.update(
            """
            UPDATE purchases
            SET subtotal = :subtotal,
                discount_total = :discountTotal,
                tax_total = :taxTotal,
                grand_total = :grandTotal,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :purchaseUuid AND status = 'DRAFT'
            """,
            params
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
            WHERE p.uuid = :uuid
            """,
            new MapSqlParameterSource("uuid", uuid),
            this::mapPurchase
        );

        if (purchases.isEmpty()) {
            return Optional.empty();
        }

        Purchase purchase = purchases.getFirst();
        List<PurchaseProduct> products = findProducts(purchase.id());

        return Optional.of(withProducts(purchase, products));
    }

    private static final Map<String, String> PURCHASE_SORT_ALLOWLIST = Map.of(
        "id", "p.id",
        "purchaseNumber", "p.purchase_number",
        "supplierName", "s.supplier_name",
        "supplierInvoiceNo", "p.supplier_invoice_no",
        "purchaseDate", "p.purchase_date",
        "status", "p.status",
        "grandTotal", "p.grand_total",
        "createdAt", "p.created_at"
    );

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
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT 501
            """,
            new MapSqlParameterSource(),
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

    @Override
    public List<Purchase> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String sortBy,
            String sortDir
    ) {
    @Override
    public List<Purchase> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String sortBy,
            String sortDir
    ) {
        return findPaged(offset, limit, search, status, null, null, null, null, false, sortBy, sortDir);
    }

    @Override
    public long countPaged(String search, String status) {
        return countPaged(search, status, null, null, null, null, false);
    }

    @Override
    public List<Purchase> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String paymentStatus,
            UUID supplierUuid,
            LocalDate fromDate,
            LocalDate toDate,
            boolean onlyOutstanding,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.uuid, p.purchase_number, p.supplier_id,
                   s.supplier_name, p.supplier_invoice_no,
                   p.purchase_date, p.status, p.subtotal,
                   p.discount_total, p.tax_total, p.grand_total,
                   p.notes, p.created_at, p.updated_at, p.received_at
            FROM purchases p
            JOIN suppliers s ON s.id = p.supplier_id
            WHERE 1=1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyPurchaseFilters(sql, params, search, status, paymentStatus, supplierUuid, fromDate, toDate, onlyOutstanding);

        String sortColumn = PURCHASE_SORT_ALLOWLIST.getOrDefault(sortBy, "p.id");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"p.id".equals(sortColumn)) {
            sql.append(", p.id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        List<Purchase> purchases = jdbcTemplate.query(sql.toString(), params, this::mapPurchase);
        List<Purchase> result = new ArrayList<>();
        for (Purchase purchase : purchases) {
            result.add(withProducts(purchase, findProducts(purchase.id())));
        }
        return result;
    }

    @Override
    public long countPaged(
            String search,
            String status,
            String paymentStatus,
            UUID supplierUuid,
            LocalDate fromDate,
            LocalDate toDate,
            boolean onlyOutstanding
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM purchases p
            JOIN suppliers s ON s.id = p.supplier_id
            WHERE 1=1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyPurchaseFilters(sql, params, search, status, paymentStatus, supplierUuid, fromDate, toDate, onlyOutstanding);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyPurchaseFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String status,
            String paymentStatus,
            UUID supplierUuid,
            LocalDate fromDate,
            LocalDate toDate,
            boolean onlyOutstanding
    ) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND (p.purchase_number ILIKE :search OR s.supplier_name ILIKE :search OR p.supplier_invoice_no ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        if (supplierUuid != null) {
            sql.append(" AND s.uuid = :supplierUuid");
            params.addValue("supplierUuid", supplierUuid);
        }
        if (fromDate != null) {
            sql.append(" AND p.purchase_date >= :fromDate");
            params.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            sql.append(" AND p.purchase_date <= :toDate");
            params.addValue("toDate", toDate);
        }
        String settledExpr = """
            (
                COALESCE((SELECT SUM(pp.amount) FROM purchase_payments pp WHERE pp.purchase_id = p.id), 0)
                + COALESCE((SELECT SUM(sca.amount) FROM supplier_credit_applications sca WHERE sca.purchase_id = p.id), 0)
            )
            """;
        if (onlyOutstanding) {
            sql.append(" AND (p.grand_total - ").append(settledExpr).append(") > 0.00");
        }
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            String ps = paymentStatus.trim().toUpperCase();
            if ("UNPAID".equals(ps)) {
                sql.append(" AND ").append(settledExpr).append(" <= 0");
            } else if ("PARTIALLY_PAID".equals(ps)) {
                sql.append(" AND ").append(settledExpr).append(" > 0 AND ").append(settledExpr).append(" < p.grand_total");
            } else if ("PAID".equals(ps)) {
                sql.append(" AND ").append(settledExpr).append(" >= p.grand_total");
            }
        }
    }

    @Override
    public boolean existsBySupplierInvoiceNoExcludingPurchase(Long supplierId, String invoiceNo, UUID excludePurchaseUuid) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM purchases
                WHERE supplier_id = :supplierId
                  AND LOWER(TRIM(supplier_invoice_no)) = LOWER(TRIM(:invoiceNo))
                  AND uuid <> :excludeUuid
            )
            """;
        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource()
                .addValue("supplierId", supplierId)
                .addValue("invoiceNo", invoiceNo)
                .addValue("excludeUuid", excludePurchaseUuid),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void updateDraftPurchase(UUID purchaseUuid, Long supplierId, String supplierInvoiceNo, LocalDate purchaseDate, String notes) {
        int updated = jdbcTemplate.update(
            """
            UPDATE purchases
            SET supplier_id = :supplierId,
                supplier_invoice_no = :invoiceNo,
                purchase_date = :purchaseDate,
                notes = :notes,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :purchaseUuid AND status = 'DRAFT'
            """,
            new MapSqlParameterSource()
                .addValue("purchaseUuid", purchaseUuid)
                .addValue("supplierId", supplierId)
                .addValue("invoiceNo", supplierInvoiceNo.trim())
                .addValue("purchaseDate", purchaseDate)
                .addValue("notes", notes)
        );
        if (updated == 0) {
            throw new IllegalStateException("Only DRAFT purchases can be updated");
        }
    }

    @Override
    public void deleteProductsByPurchaseId(Long purchaseId) {
        jdbcTemplate.update(
            "DELETE FROM purchase_products WHERE purchase_id = :purchaseId",
            new MapSqlParameterSource("purchaseId", purchaseId)
        );
    }

    @Override
    public boolean cancelDraft(UUID purchaseUuid) {
        int updated = jdbcTemplate.update(
            """
            UPDATE purchases
            SET status = 'CANCELLED',
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :purchaseUuid AND status = 'DRAFT'
            """,
            new MapSqlParameterSource("purchaseUuid", purchaseUuid)
        );
        return updated > 0;
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
            SELECT pp.id, pp.uuid, pp.product_id, pp.quantity,
                   pp.purchase_price, pp.discount_amount,
                   pp.tax_percent, pp.tax_amount, pp.line_total,
                   pr.product_code, pr.part_no, pr.name
            FROM purchase_products pp
            JOIN products pr ON pr.id = pp.product_id
            WHERE pp.purchase_id = :purchaseId
            ORDER BY pp.id
            """,
            new MapSqlParameterSource("purchaseId", purchaseId),
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
            )
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
            WHERE p.uuid = :uuid
            FOR UPDATE OF p
            """,
            new MapSqlParameterSource("uuid", uuid),
            this::mapPurchase
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
    public boolean markReceived(UUID purchaseUuid) {
        return jdbcTemplate.update(
            """
            UPDATE purchases
            SET status = 'RECEIVED',
                received_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :purchaseUuid
              AND status = 'DRAFT'
            """,
            new MapSqlParameterSource("purchaseUuid", purchaseUuid)
        ) == 1;
    }
}
