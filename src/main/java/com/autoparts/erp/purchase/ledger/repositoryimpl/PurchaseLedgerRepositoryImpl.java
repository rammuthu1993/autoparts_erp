package com.autoparts.erp.purchase.ledger.repositoryimpl;

import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerEntry;
import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerSummary;
import com.autoparts.erp.purchase.ledger.repository.PurchaseLedgerRepository;
import com.autoparts.erp.purchase.model.PurchaseStatus;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class PurchaseLedgerRepositoryImpl
    implements PurchaseLedgerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseLedgerRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String FILTER_SQL = """
        FROM purchases p
        INNER JOIN suppliers s
            ON s.id = p.supplier_id
        WHERE p.status = 'RECEIVED'
          AND (
              CAST(:fromDate AS DATE) IS NULL
              OR p.purchase_date >= CAST(:fromDate AS DATE)
          )
          AND (
              CAST(:toDate AS DATE) IS NULL
              OR p.purchase_date <= CAST(:toDate AS DATE)
          )
          AND (
              CAST(:supplierUuid AS UUID) IS NULL
              OR s.uuid = CAST(:supplierUuid AS UUID)
          )
          AND (
              :searchPattern IS NULL
              OR p.purchase_number ILIKE :searchPattern
              OR p.supplier_invoice_no ILIKE :searchPattern
              OR s.supplier_name ILIKE :searchPattern
          )
        """;

    private final RowMapper<PurchaseLedgerEntry> entryRowMapper =
        (rs, rowNum) -> new PurchaseLedgerEntry(
            rs.getObject("purchase_uuid", UUID.class),
            rs.getString("purchase_number"),
            rs.getString("supplier_name"),
            rs.getString("supplier_invoice_no"),
            rs.getObject("purchase_date", LocalDate.class),
            PurchaseStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("tax_total"),
            rs.getBigDecimal("grand_total"),
            rs.getObject("received_at", OffsetDateTime.class)
        );

    private MapSqlParameterSource filterParams(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search
    ) {

        String searchPattern = (search == null || search.isBlank())
            ? null
            : "%" + search.trim() + "%";

        return new MapSqlParameterSource()
            .addValue("fromDate", fromDate)
            .addValue("toDate", toDate)
            .addValue("supplierUuid", supplierUuid)
            .addValue("searchPattern", searchPattern);
    }

    @Override
    public List<PurchaseLedgerEntry> findEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search,
        int limit,
        long offset
    ) {

        String sql = """
            SELECT
                p.uuid AS purchase_uuid,
                p.purchase_number,
                s.supplier_name,
                p.supplier_invoice_no,
                p.purchase_date,
                p.status,
                p.subtotal,
                p.discount_total,
                p.tax_total,
                p.grand_total,
                p.received_at
            """ + FILTER_SQL + """
            ORDER BY
                p.purchase_date DESC,
                p.received_at DESC,
                p.id DESC
            LIMIT :limit OFFSET :offset
            """;

        MapSqlParameterSource params = filterParams(
            fromDate,
            toDate,
            supplierUuid,
            search
        )
        .addValue("limit", limit)
        .addValue("offset", offset);

        return jdbcTemplate.query(sql, params, entryRowMapper);
    }

    @Override
    public PurchaseLedgerSummary getSummary(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search
    ) {

        String sql = """
            SELECT
                COUNT(*) AS total_purchases,
                COALESCE(SUM(p.subtotal), 0) AS total_subtotal,
                COALESCE(SUM(p.discount_total), 0) AS total_discount,
                COALESCE(SUM(p.tax_total), 0) AS total_tax,
                COALESCE(SUM(p.grand_total), 0) AS total_purchase_amount
            """ + FILTER_SQL;

        MapSqlParameterSource params = filterParams(fromDate, toDate, supplierUuid, search);

        return jdbcTemplate.queryForObject(
            sql,
            params,
            (rs, rowNum) -> new PurchaseLedgerSummary(
                rs.getLong("total_purchases"),
                rs.getBigDecimal("total_subtotal"),
                rs.getBigDecimal("total_discount"),
                rs.getBigDecimal("total_tax"),
                rs.getBigDecimal("total_purchase_amount")
            )
        );
    }

    @Override
    public long countEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search
    ) {

        String sql = "SELECT COUNT(*) " + FILTER_SQL;

        MapSqlParameterSource params = filterParams(fromDate, toDate, supplierUuid, search);

        Long count = jdbcTemplate.queryForObject(
            sql,
            params,
            Long.class
        );

        return count == null ? 0 : count;
    }
}
