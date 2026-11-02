package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesPayment;
//import com.autoparts.erp.sales.repository.ProductStock;
import com.autoparts.erp.sales.repository.SalesRepository;

@Repository
public class SalesRepositoryImpl implements SalesRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalesRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Sales> saleRowMapper = (rs, rowNum) ->
        new Sales(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("invoice_number"),
            rs.getObject("customer_id", Long.class),
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

    private final RowMapper<SalesPayment> paymentRowMapper =
        (rs, rowNum) -> new SalesPayment(
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
    public Sales createSale(Sales sale) {

        String sql = """
            INSERT INTO sales (
                uuid,
                invoice_number,
                customer_id,
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
                ?, ?, ?, ?, ?, ?, ?, ?,
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
            sale.customerId(),
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
    public Sales updateSale(Sales sale) {

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
    public Optional<Sales> findByUuid(UUID uuid) {

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
    public Optional<Sales> findByUuidForUpdate(UUID uuid) {

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
    public List<Sales> findAll() {

        String sql = """
            SELECT *
            FROM sales
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, saleRowMapper);
    }

    @Override
    public void createSaleProduct(SalesProduct product) {

        String sql = """
            INSERT INTO sales_products (
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
            product.uuid(),
            product.saleId(),
            product.productId(),
            product.productCode(),
            product.productName(),
            product.quantity(),
            product.unit(),
            product.unitPrice(),
            product.discountAmount(),
            product.taxableAmount(),
            product.hsnCode(),
            product.gstRate(),
            product.cgstRate(),
            product.sgstRate(),
            product.igstRate(),
            product.cgstAmount(),
            product.sgstAmount(),
            product.igstAmount(),
            product.totalTaxAmount(),
            product.lineTotal()
        );
    }

    @Override
    public List<SalesProduct> findProductsBySaleId(Long saleId) {

        String sql = """
            SELECT
                sp.id,
                sp.uuid,
                sp.sale_id,
                sp.product_id,
                p.uuid AS product_uuid,
                sp.product_code,
                sp.product_name,
                sp.quantity,
                sp.unit,
                sp.unit_price,
                sp.discount_amount,
                sp.taxable_amount,
                sp.hsn_code,
                sp.gst_rate,
                sp.cgst_rate,
                sp.sgst_rate,
                sp.igst_rate,
                sp.cgst_amount,
                sp.sgst_amount,
                sp.igst_amount,
                sp.total_tax_amount,
                sp.line_total,
                sp.created_at
            FROM sales_products sp
            JOIN products p
                ON p.id = sp.product_id
            WHERE sp.sale_id = ?
            ORDER BY sp.id
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> mapSaleProduct(rs),
            saleId
        );
    }

    private SalesProduct mapSaleProduct(ResultSet rs) throws SQLException {

        return new SalesProduct(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            rs.getLong("product_id"),
            rs.getObject("product_uuid", UUID.class),
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
    public void createSalePayment(SalesPayment payment) {

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
    public List<SalesPayment> findPaymentsBySaleId(Long saleId) {

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

        String prefix = "SAL-" + saleDate.toString().replace("-", "");

        String lockSql = """
            SELECT last_number
            FROM sales_invoice_counters
            WHERE invoice_prefix = ?
            FOR UPDATE
            """;

        Long currentNumber = jdbcTemplate.query(
            lockSql,
            (rs, rowNum) -> rs.getLong("last_number"),
            prefix
        ).stream().findFirst().orElse(null);

        if (currentNumber == null) {
            try {
                jdbcTemplate.update(
                    """
                        INSERT INTO sales_invoice_counters (
                            invoice_prefix,
                            last_number,
                            updated_at
                        )
                        VALUES (?, 0, CURRENT_TIMESTAMP)
                    """,
                    prefix
                );
            } catch (DuplicateKeyException ex) {
                currentNumber = jdbcTemplate.query(
                    lockSql,
                    (rs, rowNum) -> rs.getLong("last_number"),
                    prefix
                ).stream().findFirst().orElse(0L);
            }

            currentNumber = jdbcTemplate.query(
                lockSql,
                (rs, rowNum) -> rs.getLong("last_number"),
                prefix
            ).stream().findFirst().orElse(0L);
        }

        Long nextNumber = currentNumber + 1;

        jdbcTemplate.update(
            """
                UPDATE sales_invoice_counters
                SET last_number = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE invoice_prefix = ?
            """,
            nextNumber,
            prefix
        );

        return prefix + "-" + String.format("%05d", nextNumber);
    }
    
    @Override
    public Optional<UUID> findUuidById(Long saleId) {

        String sql = """
                SELECT uuid
                FROM sales
                WHERE id = ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getObject("uuid", UUID.class),
                saleId
        ).stream().findFirst();
    }
}