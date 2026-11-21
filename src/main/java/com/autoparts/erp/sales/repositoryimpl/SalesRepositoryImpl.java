package com.autoparts.erp.sales.repositoryimpl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.repository.SalesRepository;

@Repository
public class SalesRepositoryImpl implements SalesRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
                :uuid, :invoiceNumber, :customerId, :customerName, :customerPhone, :saleDate,
                :subtotal, :discountAmount, :taxableAmount, :cgstAmount, :sgstAmount, :igstAmount,
                :totalTaxAmount, :grandTotal, :paidAmount, :balanceAmount, :status, :paymentStatus, :notes,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", sale.uuid())
                .addValue("invoiceNumber", sale.invoiceNumber())
                .addValue("customerId", sale.customerId())
                .addValue("customerName", sale.customerName())
                .addValue("customerPhone", sale.customerPhone())
                .addValue("saleDate", sale.saleDate())
                .addValue("subtotal", sale.subtotal())
                .addValue("discountAmount", sale.discountAmount())
                .addValue("taxableAmount", sale.taxableAmount())
                .addValue("cgstAmount", sale.cgstAmount())
                .addValue("sgstAmount", sale.sgstAmount())
                .addValue("igstAmount", sale.igstAmount())
                .addValue("totalTaxAmount", sale.totalTaxAmount())
                .addValue("grandTotal", sale.grandTotal())
                .addValue("paidAmount", sale.paidAmount())
                .addValue("balanceAmount", sale.balanceAmount())
                .addValue("status", sale.status().name())
                .addValue("paymentStatus", sale.paymentStatus().name())
                .addValue("notes", sale.notes());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            saleRowMapper
        );
    }

    @Override
    public Sales updateSale(Sales sale) {

        String sql = """
            UPDATE sales
            SET subtotal = :subtotal,
                discount_amount = :discountAmount,
                taxable_amount = :taxableAmount,
                cgst_amount = :cgstAmount,
                sgst_amount = :sgstAmount,
                igst_amount = :igstAmount,
                total_tax_amount = :totalTaxAmount,
                grand_total = :grandTotal,
                paid_amount = :paidAmount,
                balance_amount = :balanceAmount,
                status = :status,
                payment_status = :paymentStatus,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subtotal", sale.subtotal())
                .addValue("discountAmount", sale.discountAmount())
                .addValue("taxableAmount", sale.taxableAmount())
                .addValue("cgstAmount", sale.cgstAmount())
                .addValue("sgstAmount", sale.sgstAmount())
                .addValue("igstAmount", sale.igstAmount())
                .addValue("totalTaxAmount", sale.totalTaxAmount())
                .addValue("grandTotal", sale.grandTotal())
                .addValue("paidAmount", sale.paidAmount())
                .addValue("balanceAmount", sale.balanceAmount())
                .addValue("status", sale.status().name())
                .addValue("paymentStatus", sale.paymentStatus().name())
                .addValue("id", sale.id());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            saleRowMapper
        );
    }

    @Override
    public Optional<Sales> findByUuid(UUID uuid) {

        String sql = """
            SELECT *
            FROM sales
            WHERE uuid = :uuid
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            saleRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<Sales> findByUuidForUpdate(UUID uuid) {

        String sql = """
            SELECT *
            FROM sales
            WHERE uuid = :uuid
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            saleRowMapper
        ).stream().findFirst();
    }

    @Override
    public List<Sales> findAll() {

        String sql = """
            SELECT *
            FROM sales
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource(), saleRowMapper);
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
                :uuid, :saleId, :productId, :productCode, :productName,
                :quantity, :unit, :unitPrice, :discountAmount, :taxableAmount,
                :hsnCode, :gstRate, :cgstRate, :sgstRate, :igstRate,
                :cgstAmount, :sgstAmount, :igstAmount, :totalTaxAmount, :lineTotal
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", product.uuid())
                .addValue("saleId", product.saleId())
                .addValue("productId", product.productId())
                .addValue("productCode", product.productCode())
                .addValue("productName", product.productName())
                .addValue("quantity", product.quantity())
                .addValue("unit", product.unit())
                .addValue("unitPrice", product.unitPrice())
                .addValue("discountAmount", product.discountAmount())
                .addValue("taxableAmount", product.taxableAmount())
                .addValue("hsnCode", product.hsnCode())
                .addValue("gstRate", product.gstRate())
                .addValue("cgstRate", product.cgstRate())
                .addValue("sgstRate", product.sgstRate())
                .addValue("igstRate", product.igstRate())
                .addValue("cgstAmount", product.cgstAmount())
                .addValue("sgstAmount", product.sgstAmount())
                .addValue("igstAmount", product.igstAmount())
                .addValue("totalTaxAmount", product.totalTaxAmount())
                .addValue("lineTotal", product.lineTotal());

        jdbcTemplate.update(sql, params);
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
            WHERE sp.sale_id = :saleId
            ORDER BY sp.id
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("saleId", saleId),
            (rs, rowNum) -> mapSaleProduct(rs)
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
            VALUES (:uuid, :saleId, :paymentMethod, :amount, :referenceNumber, :paymentDate, :notes)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", payment.uuid())
                .addValue("saleId", payment.saleId())
                .addValue("paymentMethod", payment.paymentMethod().name())
                .addValue("amount", payment.amount())
                .addValue("referenceNumber", payment.referenceNumber())
                .addValue("paymentDate", payment.paymentDate())
                .addValue("notes", payment.notes());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<SalesPayment> findPaymentsBySaleId(Long saleId) {

        String sql = """
            SELECT *
            FROM sales_payments
            WHERE sale_id = :saleId
            ORDER BY payment_date, id
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("saleId", saleId),
            paymentRowMapper
        );
    }

    @Override
    public Optional<Long> findActiveProductId(UUID productUuid) {

        String sql = """
            SELECT id
            FROM products
            WHERE uuid = :productUuid
              AND active = TRUE
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("productUuid", productUuid),
            (rs, rowNum) -> rs.getLong("id")
        ).stream().findFirst();
    }

    @Override
    public boolean existsInvoiceNumber(String invoiceNumber) {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM sales
                WHERE invoice_number = :invoiceNumber
            )
            """;

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource("invoiceNumber", invoiceNumber),
            Boolean.class
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public String generateInvoiceNumber(LocalDate saleDate) {

        String prefix = "SAL-" + saleDate.toString().replace("-", "");

        String lockSql = """
            SELECT last_number
            FROM sales_invoice_counters
            WHERE invoice_prefix = :prefix
            FOR UPDATE
            """;

        Long currentNumber = jdbcTemplate.query(
            lockSql,
            new MapSqlParameterSource("prefix", prefix),
            (rs, rowNum) -> rs.getLong("last_number")
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
                        VALUES (:prefix, 0, CURRENT_TIMESTAMP)
                    """,
                    new MapSqlParameterSource("prefix", prefix)
                );
            } catch (DuplicateKeyException ex) {
                currentNumber = jdbcTemplate.query(
                    lockSql,
                    new MapSqlParameterSource("prefix", prefix),
                    (rs, rowNum) -> rs.getLong("last_number")
                ).stream().findFirst().orElse(0L);
            }

            currentNumber = jdbcTemplate.query(
                lockSql,
                new MapSqlParameterSource("prefix", prefix),
                (rs, rowNum) -> rs.getLong("last_number")
            ).stream().findFirst().orElse(0L);
        }

        Long nextNumber = currentNumber + 1;

        MapSqlParameterSource updateParams = new MapSqlParameterSource()
                .addValue("nextNumber", nextNumber)
                .addValue("prefix", prefix);

        jdbcTemplate.update(
            """
                UPDATE sales_invoice_counters
                SET last_number = :nextNumber,
                    updated_at = CURRENT_TIMESTAMP
                WHERE invoice_prefix = :prefix
            """,
            updateParams
        );

        return prefix + "-" + String.format("%05d", nextNumber);
    }
    
    @Override
    public Optional<UUID> findUuidById(Long saleId) {

        String sql = """
                SELECT uuid
                FROM sales
                WHERE id = :saleId
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("saleId", saleId),
                (rs, rowNum) -> rs.getObject("uuid", UUID.class)
        ).stream().findFirst();
    }
}
