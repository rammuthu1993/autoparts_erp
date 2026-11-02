package com.autoparts.erp.purchase.payment.repository.impl;

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

import com.autoparts.erp.purchase.model.PurchasePayment;
import com.autoparts.erp.purchase.enums.PurchasePaymentMethod;
import com.autoparts.erp.purchase.payment.repository.PurchasePaymentRepository;
import com.autoparts.erp.purchase.payment.repository.PurchasePaymentRepository.PurchasePaymentContext;

@Repository
public class PurchasePaymentRepositoryImpl
        implements PurchasePaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PurchasePaymentRepositoryImpl(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PurchasePayment> paymentRowMapper =
        this::mapPayment;

    private PurchasePayment mapPayment(
            ResultSet rs,
            int rowNum
    ) throws SQLException {

        return new PurchasePayment(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("purchase_id"),
            PurchasePaymentMethod.valueOf(
                rs.getString("payment_method")
            ),
            rs.getBigDecimal("amount"),
            rs.getString("reference_number"),
            rs.getObject("payment_date", OffsetDateTime.class),
            rs.getString("notes"),
            rs.getLong("created_by"),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    @Override
    public Optional<PurchasePaymentContext> findPurchaseForUpdate(
            UUID purchaseUuid
    ) {

        String sql = """
            SELECT
                id,
                uuid,
                purchase_number,
                status,
                grand_total
            FROM purchases
            WHERE uuid = ?
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new PurchasePaymentContext(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("purchase_number"),
                rs.getString("status"),
                rs.getBigDecimal("grand_total")
            ),
            purchaseUuid
        ).stream().findFirst();
    }

    @Override
    public BigDecimal getTotalPaid(Long purchaseId) {

        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM purchase_payments
            WHERE purchase_id = ?
            """;

        BigDecimal total = jdbcTemplate.queryForObject(
            sql,
            BigDecimal.class,
            purchaseId
        );

        return total == null ? BigDecimal.ZERO : total;
    }

    @Override
    public PurchasePayment create(PurchasePayment payment) {

        String sql = """
            INSERT INTO purchase_payments (
                uuid,
                purchase_id,
                payment_method,
                amount,
                reference_number,
                payment_date,
                notes,
                created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            paymentRowMapper,
            payment.uuid(),
            payment.purchaseId(),
            payment.paymentMethod().name(),
            payment.amount(),
            payment.referenceNumber(),
            payment.paymentDate(),
            payment.notes(),
            payment.createdBy()
        );
    }

    @Override
    public List<PurchasePayment> findByPurchaseId(
            Long purchaseId
    ) {

        String sql = """
            SELECT *
            FROM purchase_payments
            WHERE purchase_id = ?
            ORDER BY payment_date DESC, id DESC
            """;

        return jdbcTemplate.query(
            sql,
            paymentRowMapper,
            purchaseId
        );
    }

    @Override
    public Optional<PurchasePayment> findByUuid(
            UUID paymentUuid
    ) {

        String sql = """
            SELECT *
            FROM purchase_payments
            WHERE uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            paymentRowMapper,
            paymentUuid
        ).stream().findFirst();
    }
    
    @Override
    public Optional<PurchasePaymentContext> findPurchaseByUuid(
            UUID purchaseUuid
    ) {

        String sql = """
            SELECT
                id,
                uuid,
                purchase_number,
                status,
                grand_total
            FROM purchases
            WHERE uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new PurchasePaymentContext(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("purchase_number"),
                rs.getString("status"),
                rs.getBigDecimal("grand_total")
            ),
            purchaseUuid
        ).stream().findFirst();
    }
}