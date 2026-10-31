package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.model.Sale;
import com.autoparts.erp.sales.model.SaleProduct;
import com.autoparts.erp.sales.model.SalePayment;
import com.autoparts.erp.sales.repository.ProductStock;
import com.autoparts.erp.sales.repository.SalesRepository;

@Repository
public class SalesRepositoryImpl implements SalesRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalesRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Sale> saleRowMapper = (rs, rowNum) ->
        new Sale(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("invoice_number"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getObject("sale_date", LocalDate.class),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("taxable_amount"),
            rs.getBigDecimal("cgst_amount"),
            rs.getBigDecimal("sgst_amount"),
            rs.getBigDecimal("igst_amount"),
            rs.getBigDecimal("total_tax_amount"),
            rs.getBigDecimal("grand_total"),
            rs.getBigDecimal("paid_amount"),
            rs.getBigDecimal("balance_amount"),
            SalesStatus.valueOf(rs.getString("status")),
            SalesPaymentStatus.valueOf(
                rs.getString("payment_status")
            ),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );

    private final RowMapper<ProductStock> productStockMapper =
        (rs, rowNum) -> new ProductStock(
            rs.getLong("id"),
            rs.getBigDecimal("qty"),
            rs.getString("product_code"),
            rs.getString("name"),
            rs.getString("unit")
        );

    private final RowMapper<SalePayment> paymentRowMapper =
        (rs, rowNum) -> new SalePayment(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getBigDecimal("amount"),
            rs.getString("reference_number"),
            rs.getObject("payment_date", OffsetDateTime.class),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class)
        );

    @Override
    public Sale createSale(Sale sale) {

        String sql = """
            INSERT INTO sales (
                uuid,
                invoice_number,
                customer_name,
                customer_phone,
                sale_date,
                subtotal,
                discount_amount,
                taxable_amount,
                cgst_amount,
                sgst_amount,
                igst_amount,
                total_tax_amount,
                grand_total,
                paid_amount,
                balance_amount,
                status,
                payment_status,
                notes,
                created_at,
                updated_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            saleRowMapper,
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerName(),
            sale.customerPhone(),
            sale.saleDate(),
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            sale.paidAmount(),
            sale.balanceAmount(),
            sale.status().name(),
            sale.paymentStatus().name(),
            sale.notes()
        );
    }

    @Override
    public Sale updateSale(Sale sale) {

        String sql = """
            UPDATE sales
            SET subtotal = ?,
                discount_amount = ?,
                taxable_amount = ?,
                cgst_amount = ?,
                sgst_amount = ?,
                igst_amount = ?,
                total_tax_amount = ?,
                grand_total = ?,
                paid_amount = ?,
                balance_amount = ?,
                status = ?,
                payment_status = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            saleRowMapper,
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            sale.paidAmount(),
            sale.balanceAmount(),
            sale.status().name(),
            sale.paymentStatus().name(),
            sale.id()
        );
    }

    @Override
    public Optional<Sale> findByUuid(UUID uuid) {

        String sql = """
            SELECT *
            FROM sales
            WHERE uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            saleRowMapper,
            uuid
        ).stream().findFirst();
    }

    @Override
    public Optional<Sale> findByUuidForUpdate(UUID uuid) {

        String sql = """
            SELECT *
            FROM sales
            WHERE uuid = ?
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            saleRowMapper,
            uuid
        ).stream().findFirst();
    }

    @Override
    public List<Sale> findAll() {

        String sql = """
            SELECT *
            FROM sales
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, saleRowMapper);
    }

    @Override
    public void createSaleItem(SaleProduct item) {

        String sql = """
            INSERT INTO sales_items (
                uuid,
                sale_id,
                product_id,
                product_code,
                product_name,
                quantity,
                unit,
                unit_price,
                discount_amount,
                taxable_amount,
                hsn_code,
                gst_rate,
                cgst_rate,
                sgst_rate,
                igst_rate,
                cgst_amount,
                sgst_amount,
                igst_amount,
                total_tax_amount,
                line_total
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

        jdbcTemplate.update(
            sql,
            item.uuid(),
            item.saleId(),
            item.productId(),
            item.productCode(),
            item.productName(),
            item.quantity(),
            item.unit(),
            item.unitPrice(),
            item.discountAmount(),
            item.taxableAmount(),
            item.hsnCode(),
            item.gstRate(),
            item.cgstRate(),
            item.sgstRate(),
            item.igstRate(),
            item.cgstAmount(),
            item.sgstAmount(),
            item.igstAmount(),
            item.totalTaxAmount(),
            item.lineTotal()
        );
    }

    @Override
    public List<SaleProduct> findProductsBySaleId(Long saleId) {

        String sql = """
            SELECT
                si.id,
                si.uuid,
                si.sale_id,
                si.product_id,
                si.product_code,
                si.product_name,
                si.quantity,
                si.unit,
                si.unit_price,
                si.discount_amount,
                si.taxable_amount,
                si.hsn_code,
                si.gst_rate,
                si.cgst_rate,
                si.sgst_rate,
                si.igst_rate,
                si.cgst_amount,
                si.sgst_amount,
                si.igst_amount,
                si.total_tax_amount,
                si.line_total,
                si.created_at
            FROM sales_items si
            WHERE si.sale_id = ?
            ORDER BY si.id
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> mapSaleProduct(rs),
            saleId
        );
    }

    private SaleProduct mapSaleProduct(ResultSet rs) throws SQLException {

        return new SaleProduct(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            rs.getLong("product_id"),
            rs.getString("product_code"),
            rs.getString("product_name"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("taxable_amount"),
            rs.getString("hsn_code"),
            rs.getBigDecimal("gst_rate"),
            rs.getBigDecimal("cgst_rate"),
            rs.getBigDecimal("sgst_rate"),
            rs.getBigDecimal("igst_rate"),
            rs.getBigDecimal("cgst_amount"),
            rs.getBigDecimal("sgst_amount"),
            rs.getBigDecimal("igst_amount"),
            rs.getBigDecimal("total_tax_amount"),
            rs.getBigDecimal("line_total"),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    @Override
    public void createSalePayment(SalePayment payment) {

        String sql = """
            INSERT INTO sales_payments (
                uuid,
                sale_id,
                payment_method,
                amount,
                reference_number,
                payment_date,
                notes
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(
            sql,
            payment.uuid(),
            payment.saleId(),
            payment.paymentMethod().name(),
            payment.amount(),
            payment.referenceNumber(),
            payment.paymentDate(),
            payment.notes()
        );
    }

    @Override
    public List<SalePayment> findPaymentsBySaleId(Long saleId) {

        String sql = """
            SELECT *
            FROM sales_payments
            WHERE sale_id = ?
            ORDER BY payment_date, id
            """;

        return jdbcTemplate.query(
            sql,
            paymentRowMapper,
            saleId
        );
    }

    @Override
    public Optional<Long> findActiveProductId(UUID productUuid) {

        String sql = """
            SELECT id
            FROM products
            WHERE uuid = ?
              AND active = TRUE
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getLong("id"),
            productUuid
        ).stream().findFirst();
    }

    @Override
    public Optional<ProductStock> findProductStockForUpdate(
        Long productId
    ) {

        String sql = """
            SELECT id, qty, product_code, name, unit
            FROM products
            WHERE id = ?
              AND active = TRUE
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            productStockMapper,
            productId
        ).stream().findFirst();
    }

    @Override
    public void updateProductStock(
        Long productId,
        BigDecimal newQuantity
    ) {

        String sql = """
            UPDATE products
            SET qty = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        jdbcTemplate.update(sql, newQuantity, productId);
    }

    @Override
    public void createInventoryMovement(
        Long productId,
        BigDecimal quantity,
        BigDecimal beforeQuantity,
        BigDecimal afterQuantity,
        UUID saleUuid
    ) {

        String sql = """
            INSERT INTO inventory_movements (
                product_id,
                movement_type,
                quantity,
                reference_type,
                reference_id,
                before_quantity,
                after_quantity,
                created_at
            )
            VALUES (?, 'SALE', ?, 'SALE', ?, ?, ?, CURRENT_TIMESTAMP)
            """;

        jdbcTemplate.update(
            sql,
            productId,
            quantity.negate(),
            saleUuid,
            beforeQuantity,
            afterQuantity
        );
    }

    @Override
    public boolean existsInvoiceNumber(String invoiceNumber) {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM sales
                WHERE invoice_number = ?
            )
            """;

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            invoiceNumber
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public String generateInvoiceNumber(LocalDate saleDate) {

        String prefix = "SAL-" +
            saleDate.toString().replace("-", "");

        String sql = """
            SELECT COUNT(*)
            FROM sales
            WHERE invoice_number LIKE ?
            """;

        Integer count = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            prefix + "-%"
        );

        return prefix + "-" + String.format("%05d", count + 1);
    }
}