package com.autoparts.erp.sales.repositoryimpl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesReturnStatus;
import com.autoparts.erp.sales.model.SalesRefund;
import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.repository.SalesReturnRepository;

@Repository
public class SalesReturnRepositoryImpl implements SalesReturnRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesReturnRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
                :uuid, :saleId, :returnDate, :reason, :notes, :subtotal, :discountAmount,
                :taxableAmount, :cgstAmount, :sgstAmount, :igstAmount, :totalTaxAmount,
                :totalReturnAmount, :status
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", salesReturn.uuid())
                .addValue("saleId", salesReturn.saleId())
                .addValue("returnDate", salesReturn.returnDate())
                .addValue("reason", salesReturn.reason())
                .addValue("notes", salesReturn.notes())
                .addValue("subtotal", salesReturn.subtotal())
                .addValue("discountAmount", salesReturn.discountAmount())
                .addValue("taxableAmount", salesReturn.taxableAmount())
                .addValue("cgstAmount", salesReturn.cgstAmount())
                .addValue("sgstAmount", salesReturn.sgstAmount())
                .addValue("igstAmount", salesReturn.igstAmount())
                .addValue("totalTaxAmount", salesReturn.totalTaxAmount())
                .addValue("totalReturnAmount", salesReturn.totalReturnAmount())
                .addValue("status", salesReturn.status().name());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            returnRowMapper
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
                    :uuid, :salesReturnId, :salesProductId, :productId, :productCode,
                    :productName, :quantity, :unit, :unitPrice, :discountAmount,
                    :taxableAmount, :hsnCode, :gstRate, :cgstRate, :sgstRate,
                    :igstRate, :cgstAmount, :sgstAmount, :igstAmount, :totalTaxAmount,
                    :lineTotal
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", product.uuid())
                .addValue("salesReturnId", product.salesReturnId())
                .addValue("salesProductId", product.salesProductId())
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
            VALUES (
                :uuid, :salesReturnId, :paymentMethod, :amount,
                :referenceNumber, :refundDate, :notes
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", refund.uuid())
                .addValue("salesReturnId", refund.salesReturnId())
                .addValue("paymentMethod", refund.paymentMethod().name())
                .addValue("amount", refund.amount())
                .addValue("referenceNumber", refund.referenceNumber())
                .addValue("refundDate", refund.refundDate())
                .addValue("notes", refund.notes());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            refundRowMapper
        );
    }

    // ---------------------------------------------------------
    // UPDATE SALES RETURN
    // ---------------------------------------------------------

    @Override
    public SalesReturn updateReturn(SalesReturn salesReturn) {

        String sql = """
            UPDATE sales_returns
            SET subtotal = :subtotal,
                discount_amount = :discountAmount,
                taxable_amount = :taxableAmount,
                cgst_amount = :cgstAmount,
                sgst_amount = :sgstAmount,
                igst_amount = :igstAmount,
                total_tax_amount = :totalTaxAmount,
                total_return_amount = :totalReturnAmount,
                status = :status,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subtotal", salesReturn.subtotal())
                .addValue("discountAmount", salesReturn.discountAmount())
                .addValue("taxableAmount", salesReturn.taxableAmount())
                .addValue("cgstAmount", salesReturn.cgstAmount())
                .addValue("sgstAmount", salesReturn.sgstAmount())
                .addValue("igstAmount", salesReturn.igstAmount())
                .addValue("totalTaxAmount", salesReturn.totalTaxAmount())
                .addValue("totalReturnAmount", salesReturn.totalReturnAmount())
                .addValue("status", salesReturn.status().name())
                .addValue("id", salesReturn.id());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            returnRowMapper
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
            WHERE uuid = :uuid
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            returnRowMapper
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
            WHERE uuid = :uuid
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            returnRowMapper
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
            WHERE sale_id = :saleId
            ORDER BY return_date DESC, id DESC
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("saleId", saleId),
            returnRowMapper
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
            WHERE srp.sales_return_id = :returnId
            ORDER BY srp.id
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("returnId", returnId),
            returnProductRowMapper
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
            WHERE sales_return_id = :returnId
            ORDER BY refund_date, id
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("returnId", returnId),
            refundRowMapper
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
            WHERE srp.sales_product_id = :salesProductId
            ORDER BY srp.id
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("salesProductId", salesProductId),
            returnProductRowMapper
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
            WHERE sales_return_id = :returnId
            """;

        return jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource("returnId", returnId),
            BigDecimal.class
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
            WHERE sret.sale_id = :saleId
            """;

        return jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource("saleId", saleId),
            BigDecimal.class
        );
    }
}
