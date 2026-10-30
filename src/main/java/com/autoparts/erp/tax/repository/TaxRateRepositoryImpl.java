
package com.autoparts.erp.tax.repository;

import com.autoparts.erp.tax.model.TaxRate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaxRateRepositoryImpl implements TaxRateRepository {

    private final JdbcTemplate jdbcTemplate;

    public TaxRateRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TaxRate> taxRateRowMapper =
        this::mapTaxRate;

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

//    @Override
//    public void lockTaxRateConfiguration() {
//
//        jdbcTemplate.queryForObject(
//            "SELECT pg_advisory_xact_lock(824611)",
//            Object.class
//        );
//    }
    
    @Override
    public void lockTaxRateConfiguration() {
        jdbcTemplate.execute(
            "SELECT pg_advisory_xact_lock(824611)"
        );
    }

    @Override
    public boolean existsOverlappingTaxRate(
        String taxName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID excludeUuid
    ) {

//        String sql = """
//            SELECT EXISTS (
//                SELECT 1
//                FROM tax_rates
//                WHERE LOWER(TRIM(tax_name)) = LOWER(TRIM(?))
//                  AND (? IS NULL OR uuid <> ?)
//                  AND (
//                      effective_to IS NULL
//                      OR effective_to > ?
//                  )
//                  AND (
//                      ? IS NULL
//                      OR effective_from < ?
//                  )
//            )
//            """;
    	  String sql = "SELECT EXISTS (\r\n"
    	  		+ "    SELECT 1\r\n"
    	  		+ "    FROM tax_rates\r\n"
    	  		+ "    WHERE LOWER(TRIM(tax_name)) = LOWER(TRIM(?))\r\n"
    	  		+ "      AND (CAST(? AS UUID) IS NULL OR uuid <> CAST(? AS UUID))\r\n"
    	  		+ "      AND (\r\n"
    	  		+ "          effective_to IS NULL\r\n"
    	  		+ "          OR effective_to > CAST(? AS DATE)\r\n"
    	  		+ "      )\r\n"
    	  		+ "      AND (\r\n"
    	  		+ "          CAST(? AS DATE) IS NULL\r\n"
    	  		+ "          OR effective_from < CAST(? AS DATE)\r\n"
    	  		+ "      )\r\n"
    	  		+ ")";

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            taxName,
            excludeUuid,
            excludeUuid,
            effectiveFrom,
            effectiveTo,
            effectiveTo
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
                WHERE LOWER(TRIM(tax_name)) = LOWER(TRIM(?))
                  AND effective_from = ?
                  AND (? IS NULL OR uuid <> ?)
            )
            """;
//    	  String sql = """SELECT EXISTS (\r\n"
//    			    + "SELECT 1\r\n
//    			    + FROM tax_rates
//    			    WHERE LOWER(TRIM(tax_name)) = LOWER(TRIM(?))
//    			      AND effective_from = ?
//    			      AND (CAST(? AS UUID) IS NULL OR uuid <> CAST(? AS UUID))
//    			)""";

        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            taxName,
            effectiveFrom,
            excludeUuid,
            excludeUuid
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            taxRateRowMapper,
            uuid,
            taxName.trim(),
            gstRate,
            cgstRate,
            sgstRate,
            igstRate,
            effectiveFrom,
            effectiveTo
        );
    }

    @Override
    public Optional<TaxRate> findByUuid(UUID uuid) {

        String sql = SELECT_COLUMNS + """
            WHERE uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            taxRateRowMapper,
            uuid
        ).stream().findFirst();
    }

    @Override
    public List<TaxRate> findAll() {

        String sql = SELECT_COLUMNS + """
            ORDER BY effective_from DESC, tax_name
            """;

        return jdbcTemplate.query(
            sql,
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
            SET tax_name = ?,
                active = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
            RETURNING *
            """;

        return jdbcTemplate.queryForObject(
            sql,
            taxRateRowMapper,
            taxName.trim(),
            active,
            uuid
        );
    }
}
