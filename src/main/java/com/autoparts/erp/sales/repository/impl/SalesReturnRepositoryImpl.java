package com.autoparts.erp.sales.repository.impl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesReturnStatus;
import com.autoparts.erp.sales.model.SalesRefund;
import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.repository.SalesReturnRepository;

@Repository
public class SalesReturnRepositoryImpl implements SalesReturnRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalesReturnRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SalesReturn> returnRowMapper =
        (rs, rowNum) -> mapSalesReturn(rs);

    private final RowMapper<SalesRefund> refundRowMapper =
        (rs, rowNum) -> mapSalesRefund(rs);

    private final RowMapper<SalesReturnProduct> returnProductRowMapper =
        (rs, rowNum) -> mapSalesReturnProduct(rs);

    // ---------------------------------------------------------
    // SALES RETURN MAPPER
    // ---------------------------------------------------------

    private SalesReturn mapSalesReturn(ResultSet rs)
            throws SQLException {

        return new SalesReturn(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            rs.getObject("return_date", OffsetDateTime.class),
            rs.getString("reason"),
            rs.getString("notes"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("taxable_amount"),
            rs.getBigDecimal("cgst_amount"),
            rs.getBigDecimal("sgst_amount"),
            rs.getBigDecimal("igst_amount"),
            rs.getBigDecimal("total_tax_amount"),
            rs.getBigDecimal("total_return_amount"),
            SalesReturnStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    // ---------------------------------------------------------
    // SALES RETURN PRODUCT MAPPER
    // ---------------------------------------------------------

    private SalesReturnProduct mapSalesReturnProduct(ResultSet rs)
            throws SQLException {

        return new SalesReturnProduct(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sales_return_id"),
            rs.getLong("sales_product_id"),
            rs.getLong("product_id"),
            rs.getObject("product_uuid", UUID.class),
            rs.getObject("sales_product_uuid", UUID.class),
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

    // ---------------------------------------------------------
    // SALES REFUND MAPPER
    // ---------------------------------------------------------

    private SalesRefund mapSalesRefund(ResultSet rs)
            throws SQLException {

        return new SalesRefund(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sales_return_id"),
            PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getBigDecimal("amount"),
            rs.getString("reference_number"),
            rs.getObject("refund_date", OffsetDateTime.class),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    // ---------------------------------------------------------
    // CREATE SALES RETURN
    // ---------------------------------------------------------

    @Override
    public SalesReturn createReturn(SalesReturn salesReturn) {

        String sql = """
            INSERT INTO sales_returns (
                uuid,
                sale_id,
                return_date,
                reason,
                notes,
                subtotal,
                discount_amount,
                taxable_amount,
                cgst_amount,
                sgst_amount,
                igst_amount,
                total_tax_amount,
                total_return_amount,
                status
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            returnRowMapper,
            salesReturn.uuid(),
            salesReturn.saleId(),
            salesReturn.returnDate(),
            salesReturn.reason(),
            salesReturn.notes(),
            salesReturn.subtotal(),
            salesReturn.discountAmount(),
            salesReturn.taxableAmount(),
            salesReturn.cgstAmount(),
            salesReturn.sgstAmount(),
            salesReturn.igstAmount(),
            salesReturn.totalTaxAmount(),
            salesReturn.totalReturnAmount(),
            salesReturn.status().name()
        );
    }

    // ---------------------------------------------------------
    // CREATE SALES RETURN PRODUCT
    // ---------------------------------------------------------

    @Override
    public void createReturnProduct(
            SalesReturnProduct product
    ) {

        String sql = """
                INSERT INTO sales_return_products (
                    uuid,
                    sales_return_id,
                    sales_product_id,
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
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """;

        jdbcTemplate.update(
                sql,
                product.uuid(),
                product.salesReturnId(),
                product.salesProductId(),
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

    // ---------------------------------------------------------
    // CREATE REFUND
    // ---------------------------------------------------------

    @Override
    public SalesRefund createRefund(SalesRefund refund) {

        String sql = """
            INSERT INTO sales_refunds (
                uuid,
                sales_return_id,
                payment_method,
                amount,
                reference_number,
                refund_date,
                notes
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            refundRowMapper,
            refund.uuid(),
            refund.salesReturnId(),
            refund.paymentMethod().name(),
            refund.amount(),
            refund.referenceNumber(),
            refund.refundDate(),
            refund.notes()
        );
    }

    // ---------------------------------------------------------
    // UPDATE SALES RETURN
    // ---------------------------------------------------------

    @Override
    public SalesReturn updateReturn(SalesReturn salesReturn) {

        String sql = """
            UPDATE sales_returns
            SET subtotal = ?,
                discount_amount = ?,
                taxable_amount = ?,
                cgst_amount = ?,
                sgst_amount = ?,
                igst_amount = ?,
                total_tax_amount = ?,
                total_return_amount = ?,
                status = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            returnRowMapper,
            salesReturn.subtotal(),
            salesReturn.discountAmount(),
            salesReturn.taxableAmount(),
            salesReturn.cgstAmount(),
            salesReturn.sgstAmount(),
            salesReturn.igstAmount(),
            salesReturn.totalTaxAmount(),
            salesReturn.totalReturnAmount(),
            salesReturn.status().name(),
            salesReturn.id()
        );
    }

    // ---------------------------------------------------------
    // FIND RETURN BY UUID
    // ---------------------------------------------------------

    @Override
    public Optional<SalesReturn> findReturnByUuid(UUID uuid) {

        String sql = """
            SELECT *
            FROM sales_returns
            WHERE uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            returnRowMapper,
            uuid
        ).stream().findFirst();
    }

    // ---------------------------------------------------------
    // FIND RETURN BY UUID FOR UPDATE
    // ---------------------------------------------------------

    @Override
    public Optional<SalesReturn> findReturnByUuidForUpdate(UUID uuid) {

        String sql = """
            SELECT *
            FROM sales_returns
            WHERE uuid = ?
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            returnRowMapper,
            uuid
        ).stream().findFirst();
    }

    // ---------------------------------------------------------
    // FIND RETURNS BY SALE ID
    // ---------------------------------------------------------

    @Override
    public List<SalesReturn> findReturnsBySaleId(Long saleId) {

        String sql = """
            SELECT *
            FROM sales_returns
            WHERE sale_id = ?
            ORDER BY return_date DESC, id DESC
            """;

        return jdbcTemplate.query(
            sql,
            returnRowMapper,
            saleId
        );
    }

    // ---------------------------------------------------------
    // FIND RETURN PRODUCTS
    // ---------------------------------------------------------

    @Override
    public List<SalesReturnProduct> findProductsByReturnId(
            Long returnId
    ) {

        String sql = """
            SELECT
                srp.*,
                sp.uuid AS sales_product_uuid,
                p.uuid AS product_uuid
            FROM sales_return_products srp
            JOIN sales_products sp
                ON sp.id = srp.sales_product_id
            JOIN products p
                ON p.id = srp.product_id
            WHERE srp.sales_return_id = ?
            ORDER BY srp.id
            """;

        return jdbcTemplate.query(
            sql,
            returnProductRowMapper,
            returnId
        );
    }

    // ---------------------------------------------------------
    // FIND REFUNDS
    // ---------------------------------------------------------

    @Override
    public List<SalesRefund> findRefundsByReturnId(Long returnId) {

        String sql = """
            SELECT *
            FROM sales_refunds
            WHERE sales_return_id = ?
            ORDER BY refund_date, id
            """;

        return jdbcTemplate.query(
            sql,
            refundRowMapper,
            returnId
        );
    }

    // ---------------------------------------------------------
    // FIND PREVIOUS RETURNS FOR ORIGINAL SALES PRODUCT
    // ---------------------------------------------------------

    @Override
    public List<SalesReturnProduct> findReturnedProductsBySaleProductId(
            Long salesProductId
    ) {

        String sql = """
            SELECT
                srp.*,
                sp.uuid AS sales_product_uuid,
                p.uuid AS product_uuid
            FROM sales_return_products srp
            JOIN sales_products sp
                ON sp.id = srp.sales_product_id
            JOIN products p
                ON p.id = srp.product_id
            WHERE srp.sales_product_id = ?
            ORDER BY srp.id
            """;

        return jdbcTemplate.query(
            sql,
            returnProductRowMapper,
            salesProductId
        );
    }

    // ---------------------------------------------------------
    // TOTAL REFUNDED AMOUNT FOR ONE RETURN
    // ---------------------------------------------------------

    @Override
    public BigDecimal getRefundedAmount(Long returnId) {

        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM sales_refunds
            WHERE sales_return_id = ?
            """;

        return jdbcTemplate.queryForObject(
            sql,
            BigDecimal.class,
            returnId
        );
    }

    // ---------------------------------------------------------
    // TOTAL REFUNDED AMOUNT FOR A SALE
    // ---------------------------------------------------------

    @Override
    public BigDecimal getTotalRefundedAmountBySaleId(Long saleId) {

        String sql = """
            SELECT COALESCE(SUM(sr.amount), 0)
            FROM sales_refunds sr
            JOIN sales_returns sret
                ON sret.id = sr.sales_return_id
            WHERE sret.sale_id = ?
            """;

        return jdbcTemplate.queryForObject(
            sql,
            BigDecimal.class,
            saleId
        );
    }
}