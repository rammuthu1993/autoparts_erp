package com.autoparts.erp.tax.repositoryimpl;

import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductTaxMappingRepositoryImpl implements ProductTaxMappingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductTaxMappingRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProductTaxMapping> rowMapper = this::mapProductTaxMapping;

    private ProductTaxMapping mapProductTaxMapping(ResultSet rs, int rowNum) throws SQLException {
        return new ProductTaxMapping(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("product_id"),
            rs.getString("product_code"),
            rs.getString("product_name"),
            rs.getObject("tax_rate_uuid", UUID.class),
            rs.getString("tax_name"),
            rs.getBigDecimal("gst_rate"),
            rs.getBigDecimal("cgst_rate"),
            rs.getBigDecimal("sgst_rate"),
            rs.getBigDecimal("igst_rate"),
            rs.getString("hsn_code"),
            rs.getString("hsn_description"),
            rs.getObject("effective_from", LocalDate.class),
            rs.getObject("effective_to", LocalDate.class),
            rs.getString("change_reason"),
            rs.getString("created_by"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private static final String SELECT_COLUMNS = """
        SELECT
            ptm.id,
            ptm.uuid,
            ptm.product_id,
            p.product_code,
            p.name AS product_name,
            tr.uuid AS tax_rate_uuid,
            tr.tax_name,
            tr.gst_rate,
            tr.cgst_rate,
            tr.sgst_rate,
            tr.igst_rate,
            ptm.hsn_code,
            ptm.hsn_description,
            ptm.effective_from,
            ptm.effective_to,
            ptm.change_reason,
            ptm.created_by,
            ptm.created_at,
            ptm.updated_at
        FROM product_tax_mapping ptm
        JOIN products p ON p.id = ptm.product_id
        JOIN tax_rates tr ON tr.id = ptm.tax_rate_id
        """;

    @Override
    public Optional<Long> lockActiveProduct(UUID productUuid) {
        String sql = """
            SELECT id
            FROM products
            WHERE uuid = :productUuid
              AND active = TRUE
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("productUuid", productUuid),
            (rs, rowNum) -> rs.getLong("id")
        ).stream().findFirst();
    }

    @Override
    public boolean existsOverlappingMapping(
        Long productId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID excludeMappingUuid
    ) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM product_tax_mapping
                WHERE product_id = :productId
                  AND (CAST(:excludeMappingUuid AS UUID) IS NULL OR uuid <> CAST(:excludeMappingUuid AS UUID))
                  AND (
                      effective_to IS NULL
                      OR effective_to > CAST(:effectiveFrom AS DATE)
                  )
                  AND (
                      CAST(:effectiveTo AS DATE) IS NULL
                      OR effective_from < CAST(:effectiveTo AS DATE)
                  )
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("productId", productId)
            .addValue("excludeMappingUuid", excludeMappingUuid)
            .addValue("effectiveFrom", effectiveFrom)
            .addValue("effectiveTo", effectiveTo);

        Boolean exists = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public ProductTaxMapping create(
        UUID uuid,
        Long productId,
        Long taxRateId,
        String hsnCode,
        String hsnDescription,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String changeReason,
        String createdBy
    ) {
        String sql = """
            INSERT INTO product_tax_mapping (
                uuid,
                product_id,
                tax_rate_id,
                hsn_code,
                hsn_description,
                effective_from,
                effective_to,
                change_reason,
                created_by
            )
            VALUES (
                :uuid,
                :productId,
                :taxRateId,
                :hsnCode,
                :hsnDescription,
                :effectiveFrom,
                :effectiveTo,
                :changeReason,
                :createdBy
            )
            RETURNING uuid
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", uuid)
            .addValue("productId", productId)
            .addValue("taxRateId", taxRateId)
            .addValue("hsnCode", hsnCode == null || hsnCode.isBlank() ? null : hsnCode.trim())
            .addValue("hsnDescription", hsnDescription == null || hsnDescription.isBlank() ? null : hsnDescription.trim())
            .addValue("effectiveFrom", effectiveFrom)
            .addValue("effectiveTo", effectiveTo)
            .addValue("changeReason", changeReason == null || changeReason.isBlank() ? null : changeReason.trim())
            .addValue("createdBy", createdBy == null || createdBy.isBlank() ? null : createdBy.trim());

        UUID createdUuid = jdbcTemplate.queryForObject(sql, params, UUID.class);

        return findByUuid(createdUuid)
            .orElseThrow(() -> new IllegalStateException("Created product tax mapping could not be retrieved"));
    }

    @Override
    public Optional<ProductTaxMapping> findByUuid(UUID uuid) {
        String sql = SELECT_COLUMNS + """
            WHERE ptm.uuid = :uuid
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("uuid", uuid), rowMapper)
            .stream().findFirst();
    }

    @Override
    public List<ProductTaxMapping> findByProductUuid(UUID productUuid) {
        String sql = SELECT_COLUMNS + """
            WHERE p.uuid = :productUuid
            ORDER BY ptm.effective_from DESC, ptm.id DESC
            LIMIT 50
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("productUuid", productUuid), rowMapper);
    }

    @Override
    public List<ProductTaxMapping> findPagedByProductUuid(
        UUID productUuid,
        int offset,
        int limit,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("productUuid", productUuid)
            .addValue("limit", limit)
            .addValue("offset", offset);

        String where = buildProductHistoryWhere(fromDate, toDate, params);
        String sql = SELECT_COLUMNS + where
            + " ORDER BY ptm.effective_from DESC, ptm.id DESC LIMIT :limit OFFSET :offset";

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public long countPagedByProductUuid(
        UUID productUuid,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource("productUuid", productUuid);
        String where = buildProductHistoryWhere(fromDate, toDate, params);
        String sql = """
            SELECT COUNT(*)
            FROM product_tax_mapping ptm
            JOIN products p ON p.id = ptm.product_id
            """ + where;

        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    private String buildProductHistoryWhere(
        LocalDate fromDate,
        LocalDate toDate,
        MapSqlParameterSource params
    ) {
        StringBuilder where = new StringBuilder(" WHERE p.uuid = :productUuid");
        if (fromDate != null) {
            where.append(" AND (ptm.effective_to IS NULL OR ptm.effective_to > CAST(:fromDate AS DATE))");
            params.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            where.append(" AND ptm.effective_from <= CAST(:toDate AS DATE)");
            params.addValue("toDate", toDate);
        }
        return where.toString();
    }

    @Override
    public Optional<ProductTaxMapping> findCurrentByProductUuid(UUID productUuid) {
        String sql = SELECT_COLUMNS + """
            WHERE p.uuid = :productUuid
              AND ptm.effective_from <= CURRENT_DATE
              AND (ptm.effective_to IS NULL OR ptm.effective_to > CURRENT_DATE)
              AND tr.active = TRUE
              AND tr.effective_from <= CURRENT_DATE
              AND (tr.effective_to IS NULL OR tr.effective_to > CURRENT_DATE)
            ORDER BY ptm.effective_from DESC, ptm.id DESC
            LIMIT 1
            """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource("productUuid", productUuid), rowMapper)
            .stream().findFirst();
    }

    @Override
    public Optional<ProductTaxMapping> findEffectiveOnDate(Long productId, LocalDate targetDate) {
        String sql = SELECT_COLUMNS + """
            WHERE ptm.product_id = :productId
              AND ptm.effective_from <= CAST(:targetDate AS DATE)
              AND (ptm.effective_to IS NULL OR ptm.effective_to > CAST(:targetDate AS DATE))
            ORDER BY ptm.effective_from DESC, ptm.id DESC
            LIMIT 1
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource().addValue("productId", productId).addValue("targetDate", targetDate),
            rowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<ProductTaxMapping> findMappingByProductUuidAndDate(
        UUID productUuid,
        LocalDate saleDate
    ) {
        // Historical/transaction-date lookup intentionally does NOT filter out tr.active = FALSE,
        // so deactivating a rate for future use never breaks historical invoice/return lookups.
        String sql = SELECT_COLUMNS + """
            WHERE p.uuid = :productUuid
              AND ptm.effective_from <= CAST(:saleDate AS DATE)
              AND (ptm.effective_to IS NULL OR ptm.effective_to > CAST(:saleDate AS DATE))
              AND tr.effective_from <= CAST(:saleDate AS DATE)
              AND (tr.effective_to IS NULL OR tr.effective_to > CAST(:saleDate AS DATE))
            ORDER BY ptm.effective_from DESC, ptm.id DESC
            LIMIT 1
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("productUuid", productUuid)
            .addValue("saleDate", saleDate);

        return jdbcTemplate.query(sql, params, rowMapper).stream().findFirst();
    }

    @Override
    public ProductTaxMapping closePeriod(
        UUID mappingUuid,
        LocalDate effectiveTo,
        String changeReason
    ) {
        String sql = """
            UPDATE product_tax_mapping
            SET effective_to = :effectiveTo,
                change_reason = :changeReason,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :mappingUuid
            """;

        jdbcTemplate.update(
            sql,
            new MapSqlParameterSource()
                .addValue("mappingUuid", mappingUuid)
                .addValue("effectiveTo", effectiveTo)
                .addValue("changeReason", changeReason.trim())
        );

        return findByUuid(mappingUuid)
            .orElseThrow(() -> new IllegalStateException("Closed product tax mapping could not be retrieved"));
    }

    @Override
    public Optional<UUID> findProductUuidById(Long productId) {
        String sql = """
            SELECT uuid
            FROM products
            WHERE id = :productId
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("productId", productId),
            (rs, rowNum) -> rs.getObject("uuid", UUID.class)
        ).stream().findFirst();
    }
}
