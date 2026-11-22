package com.autoparts.erp.purchase.payment.repositoryimpl;

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

import com.autoparts.erp.purchase.model.PurchasePayment;
import com.autoparts.erp.purchase.enums.PurchasePaymentMethod;
import com.autoparts.erp.purchase.payment.repository.PurchasePaymentRepository;
import com.autoparts.erp.purchase.payment.repository.PurchasePaymentRepository.PurchasePaymentContext;

@Repository
public class PurchasePaymentRepositoryImpl
        implements PurchasePaymentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchasePaymentRepositoryImpl(
            NamedParameterJdbcTemplate jdbcTemplate
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
            WHERE uuid = :purchaseUuid
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("purchaseUuid", purchaseUuid),
            (rs, rowNum) -> new PurchasePaymentContext(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("purchase_number"),
                rs.getString("status"),
                rs.getBigDecimal("grand_total")
            )
        ).stream().findFirst();
    }

    @Override
    public BigDecimal getTotalPaid(Long purchaseId) {

        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM purchase_payments
            WHERE purchase_id = :purchaseId
            """;

        BigDecimal total = jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource("purchaseId", purchaseId),
            BigDecimal.class
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
            VALUES (
                :uuid,
                :purchaseId,
                :paymentMethod,
                :amount,
                :referenceNumber,
                :paymentDate,
                :notes,
                :createdBy
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", payment.uuid())
                .addValue("purchaseId", payment.purchaseId())
                .addValue("paymentMethod", payment.paymentMethod().name())
                .addValue("amount", payment.amount())
                .addValue("referenceNumber", payment.referenceNumber())
                .addValue("paymentDate", payment.paymentDate())
                .addValue("notes", payment.notes())
                .addValue("createdBy", payment.createdBy());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            paymentRowMapper
        );
    }

    @Override
    public List<PurchasePayment> findByPurchaseId(
            Long purchaseId
    ) {

        String sql = """
            SELECT *
            FROM purchase_payments
            WHERE purchase_id = :purchaseId
            ORDER BY payment_date DESC, id DESC
            LIMIT 100
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("purchaseId", purchaseId),
            paymentRowMapper
        );
    }

    @Override
    public Optional<PurchasePayment> findByUuid(
            UUID paymentUuid
    ) {

        String sql = """
            SELECT *
            FROM purchase_payments
            WHERE uuid = :paymentUuid
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("paymentUuid", paymentUuid),
            paymentRowMapper
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
            WHERE uuid = :purchaseUuid
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("purchaseUuid", purchaseUuid),
            (rs, rowNum) -> new PurchasePaymentContext(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("purchase_number"),
                rs.getString("status"),
                rs.getBigDecimal("grand_total")
            )
        ).stream().findFirst();
    }
}
