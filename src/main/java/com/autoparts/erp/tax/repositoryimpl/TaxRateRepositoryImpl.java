package com.autoparts.erp.tax.repositoryimpl;

import com.autoparts.erp.tax.dto.TaxSummaryResponse;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.TaxRateRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaxRateRepositoryImpl implements TaxRateRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TaxRateRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TaxRate> taxRateRowMapper = this::mapTaxRate;

    private TaxRate mapTaxRate(ResultSet rs, int rowNum) throws SQLException {
        return new TaxRate(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("tax_name"),
            rs.getBigDecimal("gst_rate"),
            rs.getBigDecimal("cgst_rate"),
            rs.getBigDecimal("sgst_rate"),
            rs.getBigDecimal("igst_rate"),
            rs.getObject("effective_from", LocalDate.class),
            rs.getObject("effective_to", LocalDate.class),
            rs.getBoolean("active"),
            rs.getString("change_reason"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private static final String SELECT_COLUMNS = """
        SELECT
            id,
            uuid,
            tax_name,
            gst_rate,
            cgst_rate,
            sgst_rate,
            igst_rate,
            effective_from,
            effective_to,
            active,
            change_reason,
            created_at,
            updated_at
        FROM tax_rates
        """;

    private static final Map<String, String> TAX_RATE_SORT_ALLOWLIST = Map.of(
        "id", "id",
        "taxName", "tax_name",
        "gstRate", "gst_rate",
        "effectiveFrom", "effective_from",
        "effectiveTo", "effective_to",
        "createdAt", "created_at"
    );

    @Override
    public void lockTaxRateConfiguration() {
        jdbcTemplate.execute(
            "SELECT pg_advisory_xact_lock(:lockKey)",
            new MapSqlParameterSource("lockKey", 824611L),
            PreparedStatement::execute
        );
    }

    @Override
    public boolean existsOverlappingTaxRate(
        String taxName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID excludeUuid
    ) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM tax_rates
                WHERE LOWER(TRIM(tax_name)) = LOWER(TRIM(:taxName))
                  AND (CAST(:excludeUuid AS UUID) IS NULL OR uuid <> CAST(:excludeUuid AS UUID))
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
            .addValue("taxName", taxName)
            .addValue("excludeUuid", excludeUuid)
            .addValue("effectiveFrom", effectiveFrom)
            .addValue("effectiveTo", effectiveTo);

        Boolean exists = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByNameAndEffectiveFrom(
        String taxName,
        LocalDate effectiveFrom,
        UUID excludeUuid
    ) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM tax_rates
                WHERE LOWER(TRIM(tax_name)) = LOWER(TRIM(:taxName))
                  AND effective_from = :effectiveFrom
                  AND (CAST(:excludeUuid AS UUID) IS NULL OR uuid <> CAST(:excludeUuid AS UUID))
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("taxName", taxName)
            .addValue("effectiveFrom", effectiveFrom)
            .addValue("excludeUuid", excludeUuid);

        Boolean exists = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public TaxRate create(
        UUID uuid,
        String taxName,
        BigDecimal gstRate,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal igstRate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        String sql = """
            INSERT INTO tax_rates (
                uuid,
                tax_name,
                gst_rate,
                cgst_rate,
                sgst_rate,
                igst_rate,
                effective_from,
                effective_to
            )
            VALUES (
                :uuid,
                :taxName,
                :gstRate,
                :cgstRate,
                :sgstRate,
                :igstRate,
                :effectiveFrom,
                :effectiveTo
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", uuid)
            .addValue("taxName", taxName.trim())
            .addValue("gstRate", gstRate)
            .addValue("cgstRate", cgstRate)
            .addValue("sgstRate", sgstRate)
            .addValue("igstRate", igstRate)
            .addValue("effectiveFrom", effectiveFrom)
            .addValue("effectiveTo", effectiveTo);

        return jdbcTemplate.queryForObject(sql, params, taxRateRowMapper);
    }

    @Override
    public Optional<TaxRate> findCurrentByGstRate(BigDecimal gstRate, LocalDate onDate) {
        String sql = SELECT_COLUMNS + """
            WHERE active = TRUE
              AND gst_rate = :gstRate
              AND effective_from <= :onDate
              AND (effective_to IS NULL OR effective_to > :onDate)
            ORDER BY effective_from DESC, id DESC
            LIMIT 1
            """;
        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource().addValue("gstRate", gstRate).addValue("onDate", onDate),
            taxRateRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<TaxRate> findByUuid(UUID uuid) {
        String sql = SELECT_COLUMNS + """
            WHERE uuid = :uuid
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("uuid", uuid), taxRateRowMapper)
            .stream().findFirst();
    }

    @Override
    public List<TaxRate> findAll() {
        String sql = SELECT_COLUMNS + """
            ORDER BY effective_from DESC, tax_name, id DESC
            LIMIT 101
            """;
        return jdbcTemplate.query(sql, EmptySqlParameterSource.INSTANCE, taxRateRowMapper);
    }

    @Override
    public List<TaxRate> findPaged(
        int offset,
        int limit,
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn,
        String sortBy,
        String sortDir
    ) {
        String sortColumn = TAX_RATE_SORT_ALLOWLIST.getOrDefault(sortBy, "effective_from");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit)
            .addValue("offset", offset);

        String whereClause = buildFilterClause(search, gstRate, active, periodStatus, effectiveOn, params);
        String sql = SELECT_COLUMNS + whereClause
            + " ORDER BY " + sortColumn + " " + direction + ", id DESC LIMIT :limit OFFSET :offset";

        return jdbcTemplate.query(sql, params, taxRateRowMapper);
    }

    @Override
    public long countPaged(
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildFilterClause(search, gstRate, active, periodStatus, effectiveOn, params);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tax_rates" + whereClause, params, Long.class);
        return count != null ? count : 0L;
    }

    private String buildFilterClause(
        String search,
        BigDecimal gstRate,
        Boolean active,
        String periodStatus,
        LocalDate effectiveOn,
        MapSqlParameterSource params
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (search != null && !search.isBlank()) {
            where.append(" AND LOWER(tax_name) LIKE :search");
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (gstRate != null) {
            where.append(" AND gst_rate = :gstRate");
            params.addValue("gstRate", gstRate);
        }
        if (active != null) {
            where.append(" AND active = :active");
            params.addValue("active", active);
        }
        if (effectiveOn != null) {
            where.append(" AND effective_from <= CAST(:effectiveOn AS DATE)")
                 .append(" AND (effective_to IS NULL OR effective_to > CAST(:effectiveOn AS DATE))");
            params.addValue("effectiveOn", effectiveOn);
        }
        if (periodStatus != null && !periodStatus.isBlank()) {
            switch (periodStatus.trim().toUpperCase()) {
                case "CURRENT" -> where.append(" AND active = TRUE AND effective_from <= CURRENT_DATE AND (effective_to IS NULL OR effective_to > CURRENT_DATE)");
                case "UPCOMING" -> where.append(" AND active = TRUE AND effective_from > CURRENT_DATE");
                case "EXPIRED" -> where.append(" AND effective_to IS NOT NULL AND effective_to <= CURRENT_DATE");
                case "INACTIVE" -> where.append(" AND active = FALSE");
                default -> { }
            }
        }
        return where.toString();
    }

    @Override
    public List<TaxRate> findCurrent() {
        String sql = SELECT_COLUMNS + """
            WHERE active = TRUE
              AND effective_from <= CURRENT_DATE
              AND (
                  effective_to IS NULL
                  OR effective_to > CURRENT_DATE
              )
            ORDER BY gst_rate, tax_name
            """;
        return jdbcTemplate.query(sql, EmptySqlParameterSource.INSTANCE, taxRateRowMapper);
    }

    @Override
    public List<TaxRate> findSelectable(LocalDate effectiveDate) {
        LocalDate target = effectiveDate != null ? effectiveDate : LocalDate.now();
        String sql = SELECT_COLUMNS + """
            WHERE active = TRUE
              AND effective_from <= CAST(:targetDate AS DATE)
              AND (effective_to IS NULL OR effective_to > CAST(:targetDate AS DATE))
            ORDER BY gst_rate, tax_name, effective_from DESC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("targetDate", target), taxRateRowMapper);
    }

    @Override
    public TaxSummaryResponse getSummary() {
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM tax_rates
                 WHERE active = TRUE AND effective_from <= CURRENT_DATE AND (effective_to IS NULL OR effective_to > CURRENT_DATE)) AS current_rates,
                (SELECT COUNT(*) FROM tax_rates
                 WHERE active = TRUE AND effective_from > CURRENT_DATE) AS upcoming_rates,
                (SELECT COUNT(*) FROM tax_rates
                 WHERE effective_to IS NOT NULL AND effective_to <= CURRENT_DATE) AS expired_rates,
                (SELECT COUNT(*) FROM tax_rates WHERE active = FALSE) AS inactive_rates,
                (SELECT COUNT(DISTINCT p.id) FROM products p
                 JOIN product_tax_mapping ptm ON ptm.product_id = p.id
                 WHERE p.active = TRUE
                   AND ptm.effective_from <= CURRENT_DATE
                   AND (ptm.effective_to IS NULL OR ptm.effective_to > CURRENT_DATE)) AS products_with_current,
                (SELECT COUNT(*) FROM products p
                 WHERE p.active = TRUE
                   AND NOT EXISTS (
                       SELECT 1 FROM product_tax_mapping ptm
                       WHERE ptm.product_id = p.id
                         AND ptm.effective_from <= CURRENT_DATE
                         AND (ptm.effective_to IS NULL OR ptm.effective_to > CURRENT_DATE)
                   )) AS products_missing,
                (SELECT COUNT(DISTINCT p.id) FROM products p
                 JOIN product_tax_mapping ptm ON ptm.product_id = p.id
                 WHERE p.active = TRUE AND ptm.effective_from > CURRENT_DATE) AS products_with_upcoming
            """;
        return jdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, (rs, rowNum) ->
            new TaxSummaryResponse(
                rs.getLong("current_rates"),
                rs.getLong("upcoming_rates"),
                rs.getLong("expired_rates"),
                rs.getLong("inactive_rates"),
                rs.getLong("products_with_current"),
                rs.getLong("products_missing"),
                rs.getLong("products_with_upcoming")
            )
        );
    }

    @Override
    public TaxRate update(
        UUID uuid,
        String taxName,
        boolean active,
        String changeReason
    ) {
        String sql = """
            UPDATE tax_rates
            SET tax_name = :taxName,
                active = :active,
                change_reason = COALESCE(:changeReason, change_reason),
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", uuid)
            .addValue("taxName", taxName.trim())
            .addValue("active", active)
            .addValue("changeReason", changeReason != null && !changeReason.isBlank() ? changeReason.trim() : null);

        return jdbcTemplate.queryForObject(sql, params, taxRateRowMapper);
    }

    @Override
    public TaxRate closePeriod(
        UUID uuid,
        LocalDate effectiveTo,
        String changeReason
    ) {
        String sql = """
            UPDATE tax_rates
            SET effective_to = :effectiveTo,
                change_reason = :changeReason,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", uuid)
            .addValue("effectiveTo", effectiveTo)
            .addValue("changeReason", changeReason.trim());

        return jdbcTemplate.queryForObject(sql, params, taxRateRowMapper);
    }
}
