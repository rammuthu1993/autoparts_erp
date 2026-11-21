package com.autoparts.erp.tax.repositoryimpl;

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
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaxRateRepositoryImpl implements TaxRateRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TaxRateRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TaxRate> taxRateRowMapper = this::mapTaxRate;

    private TaxRate mapTaxRate(
        ResultSet rs,
        int rowNum
    ) throws SQLException {
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
            created_at,
            updated_at
        FROM tax_rates
        """;

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

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            params,
            Boolean.class
        );

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

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            params,
            Boolean.class
        );

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

        return jdbcTemplate.queryForObject(
            sql,
            params,
            taxRateRowMapper
        );
    }

    @Override
    public Optional<TaxRate> findByUuid(UUID uuid) {
        String sql = SELECT_COLUMNS + """
            WHERE uuid = :uuid
            """;

        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("uuid", uuid),
            taxRateRowMapper
        ).stream().findFirst();
    }

    @Override
    public List<TaxRate> findAll() {
        String sql = SELECT_COLUMNS + """
            ORDER BY effective_from DESC, tax_name
            """;

        return jdbcTemplate.query(
            sql,
            EmptySqlParameterSource.INSTANCE,
            taxRateRowMapper
        );
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

        return jdbcTemplate.query(
            sql,
            EmptySqlParameterSource.INSTANCE,
            taxRateRowMapper
        );
    }

    @Override
    public TaxRate update(
        UUID uuid,
        String taxName,
        boolean active
    ) {
        String sql = """
            UPDATE tax_rates
            SET tax_name = :taxName,
                active = :active,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", uuid)
            .addValue("taxName", taxName.trim())
            .addValue("active", active);

        return jdbcTemplate.queryForObject(
            sql,
            params,
            taxRateRowMapper
        );
    }
}