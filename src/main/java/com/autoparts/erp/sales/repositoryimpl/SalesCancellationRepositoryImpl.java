package com.autoparts.erp.sales.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.sales.dto.SalesCancellationResponse;
import com.autoparts.erp.sales.enums.CancellationRefundStatus;
import com.autoparts.erp.sales.enums.GstAdjustmentType;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.model.SalesCancellationRefund;
import com.autoparts.erp.sales.model.SalesInvoiceCancellation;
import com.autoparts.erp.sales.model.SalesInvoiceCancellationItem;
import com.autoparts.erp.sales.repository.SalesCancellationRepository;

@Repository
public class SalesCancellationRepositoryImpl implements SalesCancellationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesCancellationRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SalesInvoiceCancellation> cancellationRowMapper = (rs, rowNum) ->
        mapCancellation(rs);

    private final RowMapper<SalesInvoiceCancellationItem> itemRowMapper = (rs, rowNum) ->
        new SalesInvoiceCancellationItem(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("cancellation_id"),
            rs.getLong("sales_product_id"),
            rs.getLong("product_id"),
            rs.getString("product_code"),
            rs.getString("product_name"),
            rs.getBigDecimal("original_quantity"),
            rs.getBigDecimal("previously_returned_quantity"),
            rs.getBigDecimal("restored_quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("taxable_reversed"),
            rs.getBigDecimal("cgst_reversed"),
            rs.getBigDecimal("sgst_reversed"),
            rs.getBigDecimal("igst_reversed"),
            rs.getBigDecimal("total_tax_reversed"),
            rs.getBigDecimal("line_total_reversed"),
            rs.getObject("created_at", OffsetDateTime.class)
        );

    private final RowMapper<SalesCancellationRefund> refundRowMapper = (rs, rowNum) ->
        new SalesCancellationRefund(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("cancellation_id"),
            rs.getLong("sale_id"),
            rs.getObject("original_payment_id", Long.class),
            PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getBigDecimal("amount"),
            CancellationRefundStatus.valueOf(rs.getString("status")),
            rs.getString("reference_number"),
            rs.getObject("refund_date", OffsetDateTime.class),
            rs.getObject("confirmed_at", OffsetDateTime.class),
            rs.getObject("confirmed_by", Long.class),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );

    private static SalesInvoiceCancellation mapCancellation(ResultSet rs) throws SQLException {
        String refundMethodStr = rs.getString("refund_method");
        return new SalesInvoiceCancellation(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            rs.getString("invoice_number"),
            rs.getString("idempotency_key"),
            rs.getString("cancellation_reason"),
            rs.getLong("cancelled_by"),
            rs.getObject("cancelled_at", OffsetDateTime.class),
            rs.getBigDecimal("original_grand_total"),
            rs.getBigDecimal("original_taxable_amount"),
            rs.getBigDecimal("original_cgst_amount"),
            rs.getBigDecimal("original_sgst_amount"),
            rs.getBigDecimal("original_igst_amount"),
            rs.getBigDecimal("original_total_tax_amount"),
            rs.getBigDecimal("previously_paid_amount"),
            rs.getBigDecimal("previously_returned_amount"),
            rs.getBigDecimal("previously_refunded_amount"),
            rs.getBigDecimal("cancelled_invoice_amount"),
            rs.getBigDecimal("refund_amount"),
            refundMethodStr != null ? PaymentMethod.valueOf(refundMethodStr) : null,
            CancellationRefundStatus.valueOf(rs.getString("refund_status")),
            rs.getString("refund_reference"),
            rs.getObject("refund_confirmed_at", OffsetDateTime.class),
            rs.getObject("refund_confirmed_by", Long.class),
            rs.getBigDecimal("receivable_adjustment_amount"),
            GstAdjustmentType.valueOf(rs.getString("gst_adjustment_type")),
            rs.getString("gst_adjustment_reference"),
            rs.getString("credit_note_number"),
            rs.getString("irn"),
            rs.getString("irn_status"),
            rs.getString("eway_bill_number"),
            rs.getString("eway_bill_status"),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    @Override
    public SalesInvoiceCancellation createCancellation(SalesInvoiceCancellation c) {
        String sql = """
            INSERT INTO sales_invoice_cancellations (
                uuid, sale_id, invoice_number, idempotency_key, cancellation_reason,
                cancelled_by, cancelled_at,
                original_grand_total, original_taxable_amount, original_cgst_amount,
                original_sgst_amount, original_igst_amount, original_total_tax_amount,
                previously_paid_amount, previously_returned_amount, previously_refunded_amount,
                cancelled_invoice_amount, refund_amount, refund_method, refund_status,
                refund_reference, refund_confirmed_at, refund_confirmed_by,
                receivable_adjustment_amount, gst_adjustment_type, gst_adjustment_reference,
                credit_note_number, irn, irn_status, eway_bill_number, eway_bill_status,
                notes
            ) VALUES (
                :uuid, :saleId, :invoiceNumber, :idempotencyKey, :cancellationReason,
                :cancelledBy, :cancelledAt,
                :originalGrandTotal, :originalTaxableAmount, :originalCgstAmount,
                :originalSgstAmount, :originalIgstAmount, :originalTotalTaxAmount,
                :previouslyPaidAmount, :previouslyReturnedAmount, :previouslyRefundedAmount,
                :cancelledInvoiceAmount, :refundAmount, :refundMethod, :refundStatus,
                :refundReference, :refundConfirmedAt, :refundConfirmedBy,
                :receivableAdjustmentAmount, :gstAdjustmentType, :gstAdjustmentReference,
                :creditNoteNumber, :irn, :irnStatus, :ewayBillNumber, :ewayBillStatus,
                :notes
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", c.uuid() != null ? c.uuid() : UUID.randomUUID())
            .addValue("saleId", c.saleId())
            .addValue("invoiceNumber", c.invoiceNumber())
            .addValue("idempotencyKey", c.idempotencyKey())
            .addValue("cancellationReason", c.cancellationReason())
            .addValue("cancelledBy", c.cancelledBy())
            .addValue("cancelledAt", c.cancelledAt() != null ? c.cancelledAt() : OffsetDateTime.now())
            .addValue("originalGrandTotal", c.originalGrandTotal())
            .addValue("originalTaxableAmount", c.originalTaxableAmount())
            .addValue("originalCgstAmount", c.originalCgstAmount())
            .addValue("originalSgstAmount", c.originalSgstAmount())
            .addValue("originalIgstAmount", c.originalIgstAmount())
            .addValue("originalTotalTaxAmount", c.originalTotalTaxAmount())
            .addValue("previouslyPaidAmount", c.previouslyPaidAmount())
            .addValue("previouslyReturnedAmount", c.previouslyReturnedAmount())
            .addValue("previouslyRefundedAmount", c.previouslyRefundedAmount())
            .addValue("cancelledInvoiceAmount", c.cancelledInvoiceAmount())
            .addValue("refundAmount", c.refundAmount())
            .addValue("refundMethod", c.refundMethod() != null ? c.refundMethod().name() : null)
            .addValue("refundStatus", c.refundStatus().name())
            .addValue("refundReference", c.refundReference())
            .addValue("refundConfirmedAt", c.refundConfirmedAt())
            .addValue("refundConfirmedBy", c.refundConfirmedBy())
            .addValue("receivableAdjustmentAmount", c.receivableAdjustmentAmount())
            .addValue("gstAdjustmentType", c.gstAdjustmentType().name())
            .addValue("gstAdjustmentReference", c.gstAdjustmentReference())
            .addValue("creditNoteNumber", c.creditNoteNumber())
            .addValue("irn", c.irn())
            .addValue("irnStatus", c.irnStatus() != null ? c.irnStatus() : "NOT_APPLICABLE")
            .addValue("ewayBillNumber", c.ewayBillNumber())
            .addValue("ewayBillStatus", c.ewayBillStatus() != null ? c.ewayBillStatus() : "NOT_APPLICABLE")
            .addValue("notes", c.notes());

        return jdbcTemplate.queryForObject(sql, params, cancellationRowMapper);
    }

    @Override
    public SalesInvoiceCancellation updateCancellation(SalesInvoiceCancellation c) {
        String sql = """
            UPDATE sales_invoice_cancellations
            SET refund_status = :refundStatus,
                refund_reference = :refundReference,
                refund_confirmed_at = :refundConfirmedAt,
                refund_confirmed_by = :refundConfirmedBy,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            RETURNING *
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", c.id())
            .addValue("refundStatus", c.refundStatus().name())
            .addValue("refundReference", c.refundReference())
            .addValue("refundConfirmedAt", c.refundConfirmedAt())
            .addValue("refundConfirmedBy", c.refundConfirmedBy());
        return jdbcTemplate.queryForObject(sql, params, cancellationRowMapper);
    }

    @Override
    public SalesInvoiceCancellationItem createCancellationItem(SalesInvoiceCancellationItem item) {
        String sql = """
            INSERT INTO sales_invoice_cancellation_items (
                uuid, cancellation_id, sales_product_id, product_id,
                product_code, product_name,
                original_quantity, previously_returned_quantity, restored_quantity, unit,
                taxable_reversed, cgst_reversed, sgst_reversed, igst_reversed,
                total_tax_reversed, line_total_reversed
            ) VALUES (
                :uuid, :cancellationId, :salesProductId, :productId,
                :productCode, :productName,
                :originalQuantity, :previouslyReturnedQuantity, :restoredQuantity, :unit,
                :taxableReversed, :cgstReversed, :sgstReversed, :igstReversed,
                :totalTaxReversed, :lineTotalReversed
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", item.uuid() != null ? item.uuid() : UUID.randomUUID())
            .addValue("cancellationId", item.cancellationId())
            .addValue("salesProductId", item.salesProductId())
            .addValue("productId", item.productId())
            .addValue("productCode", item.productCode())
            .addValue("productName", item.productName())
            .addValue("originalQuantity", item.originalQuantity())
            .addValue("previouslyReturnedQuantity", item.previouslyReturnedQuantity())
            .addValue("restoredQuantity", item.restoredQuantity())
            .addValue("unit", item.unit())
            .addValue("taxableReversed", item.taxableReversed())
            .addValue("cgstReversed", item.cgstReversed())
            .addValue("sgstReversed", item.sgstReversed())
            .addValue("igstReversed", item.igstReversed())
            .addValue("totalTaxReversed", item.totalTaxReversed())
            .addValue("lineTotalReversed", item.lineTotalReversed());

        return jdbcTemplate.queryForObject(sql, params, itemRowMapper);
    }

    @Override
    public SalesCancellationRefund createCancellationRefund(SalesCancellationRefund refund) {
        String sql = """
            INSERT INTO sales_cancellation_refunds (
                uuid, cancellation_id, sale_id, original_payment_id,
                payment_method, amount, status, reference_number,
                refund_date, confirmed_at, confirmed_by, notes
            ) VALUES (
                :uuid, :cancellationId, :saleId, :originalPaymentId,
                :paymentMethod, :amount, :status, :referenceNumber,
                :refundDate, :confirmedAt, :confirmedBy, :notes
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", refund.uuid() != null ? refund.uuid() : UUID.randomUUID())
            .addValue("cancellationId", refund.cancellationId())
            .addValue("saleId", refund.saleId())
            .addValue("originalPaymentId", refund.originalPaymentId())
            .addValue("paymentMethod", refund.paymentMethod().name())
            .addValue("amount", refund.amount())
            .addValue("status", refund.status().name())
            .addValue("referenceNumber", refund.referenceNumber())
            .addValue("refundDate", refund.refundDate() != null ? refund.refundDate() : OffsetDateTime.now())
            .addValue("confirmedAt", refund.confirmedAt())
            .addValue("confirmedBy", refund.confirmedBy())
            .addValue("notes", refund.notes());

        return jdbcTemplate.queryForObject(sql, params, refundRowMapper);
    }

    @Override
    public SalesCancellationRefund updateCancellationRefund(SalesCancellationRefund refund) {
        String sql = """
            UPDATE sales_cancellation_refunds
            SET status = :status,
                reference_number = :referenceNumber,
                confirmed_at = :confirmedAt,
                confirmed_by = :confirmedBy,
                notes = :notes,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", refund.id())
            .addValue("status", refund.status().name())
            .addValue("referenceNumber", refund.referenceNumber())
            .addValue("confirmedAt", refund.confirmedAt())
            .addValue("confirmedBy", refund.confirmedBy())
            .addValue("notes", refund.notes());

        return jdbcTemplate.queryForObject(sql, params, refundRowMapper);
    }

    @Override
    public Optional<SalesInvoiceCancellation> findBySaleId(Long saleId) {
        String sql = "SELECT * FROM sales_invoice_cancellations WHERE sale_id = :saleId";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("saleId", saleId), cancellationRowMapper)
            .stream().findFirst();
    }

    @Override
    public Optional<SalesInvoiceCancellation> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM sales_invoice_cancellations WHERE idempotency_key = :key";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("key", idempotencyKey.trim()), cancellationRowMapper)
            .stream().findFirst();
    }

    @Override
    public Optional<SalesCancellationRefund> findRefundByUuidForUpdate(UUID refundUuid) {
        String sql = "SELECT * FROM sales_cancellation_refunds WHERE uuid = :uuid FOR UPDATE";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("uuid", refundUuid), refundRowMapper)
            .stream().findFirst();
    }

    @Override
    public List<SalesInvoiceCancellationItem> findItemsByCancellationId(Long cancellationId) {
        String sql = """
            SELECT *
            FROM sales_invoice_cancellation_items
            WHERE cancellation_id = :cancellationId
            ORDER BY id ASC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cancellationId", cancellationId), itemRowMapper);
    }

    @Override
    public List<SalesCancellationRefund> findRefundsByCancellationId(Long cancellationId) {
        String sql = """
            SELECT *
            FROM sales_cancellation_refunds
            WHERE cancellation_id = :cancellationId
            ORDER BY id ASC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cancellationId", cancellationId), refundRowMapper);
    }

    @Override
    public Optional<SalesCancellationResponse> findDetailedBySaleId(Long saleId) {
        String sql = """
            SELECT c.*, s.uuid AS sale_uuid, u.username AS cancelled_by_username
            FROM sales_invoice_cancellations c
            JOIN sales s ON s.id = c.sale_id
            JOIN users u ON u.id = c.cancelled_by
            WHERE c.sale_id = :saleId
            """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource("saleId", saleId), (rs, rowNum) -> {
            SalesInvoiceCancellation c = mapCancellation(rs);
            UUID saleUuid = rs.getObject("sale_uuid", UUID.class);
            String cancelledByUsername = rs.getString("cancelled_by_username");

            List<SalesCancellationResponse.ItemResponse> items = findItemResponses(c.id());
            List<SalesCancellationResponse.RefundResponse> refunds = findRefundResponses(c.id());

            return new SalesCancellationResponse(
                c.uuid(),
                saleUuid,
                c.invoiceNumber(),
                c.cancellationReason(),
                c.cancelledBy(),
                cancelledByUsername,
                c.cancelledAt(),
                c.originalGrandTotal(),
                c.originalTaxableAmount(),
                c.originalCgstAmount(),
                c.originalSgstAmount(),
                c.originalIgstAmount(),
                c.originalTotalTaxAmount(),
                c.previouslyPaidAmount(),
                c.previouslyReturnedAmount(),
                c.previouslyRefundedAmount(),
                c.cancelledInvoiceAmount(),
                c.refundAmount(),
                c.refundMethod(),
                c.refundStatus(),
                c.refundReference(),
                c.refundConfirmedAt(),
                c.receivableAdjustmentAmount(),
                c.gstAdjustmentType(),
                c.gstAdjustmentReference(),
                c.creditNoteNumber(),
                c.irn(),
                c.irnStatus(),
                c.ewayBillNumber(),
                c.ewayBillStatus(),
                c.notes(),
                items,
                refunds
            );
        }).stream().findFirst();
    }

    private List<SalesCancellationResponse.ItemResponse> findItemResponses(Long cancellationId) {
        String sql = """
            SELECT i.*, p.uuid AS product_uuid
            FROM sales_invoice_cancellation_items i
            JOIN products p ON p.id = i.product_id
            WHERE i.cancellation_id = :cancellationId
            ORDER BY i.id ASC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cancellationId", cancellationId), (rs, rowNum) ->
            new SalesCancellationResponse.ItemResponse(
                rs.getObject("uuid", UUID.class),
                rs.getObject("product_uuid", UUID.class),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getBigDecimal("original_quantity"),
                rs.getBigDecimal("previously_returned_quantity"),
                rs.getBigDecimal("restored_quantity"),
                rs.getString("unit"),
                rs.getBigDecimal("taxable_reversed"),
                rs.getBigDecimal("cgst_reversed"),
                rs.getBigDecimal("sgst_reversed"),
                rs.getBigDecimal("igst_reversed"),
                rs.getBigDecimal("total_tax_reversed"),
                rs.getBigDecimal("line_total_reversed")
            )
        );
    }

    private List<SalesCancellationResponse.RefundResponse> findRefundResponses(Long cancellationId) {
        String sql = """
            SELECT r.*, sp.uuid AS original_payment_uuid
            FROM sales_cancellation_refunds r
            LEFT JOIN sales_payments sp ON sp.id = r.original_payment_id
            WHERE r.cancellation_id = :cancellationId
            ORDER BY r.id ASC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cancellationId", cancellationId), (rs, rowNum) ->
            new SalesCancellationResponse.RefundResponse(
                rs.getObject("uuid", UUID.class),
                rs.getObject("original_payment_uuid", UUID.class),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getBigDecimal("amount"),
                CancellationRefundStatus.valueOf(rs.getString("status")),
                rs.getString("reference_number"),
                rs.getObject("refund_date", OffsetDateTime.class),
                rs.getObject("confirmed_at", OffsetDateTime.class),
                rs.getString("notes")
            )
        );
    }
}
