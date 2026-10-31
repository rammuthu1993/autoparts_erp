
package com.autoparts.erp.tax.repository;

import com.autoparts.erp.tax.model.ProductTaxMapping;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductTaxMappingRepositoryImpl
        implements ProductTaxMappingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductTaxMappingRepositoryImpl(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProductTaxMapping> rowMapper =
        this::mapProductTaxMapping;

    private ProductTaxMapping mapProductTaxMapping(
        ResultSet rs,
        int rowNum
    ) throws SQLException {

        return new ProductTaxMapping(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("product_id"),
            rs.getString("product_code"),
            rs.getString("product_name"),
            rs.getObject("tax_rate_uuid", UUID.class),
            rs.getString("tax_name"),
            rs.getString("hsn_code"),
            rs.getObject("effective_from", LocalDate.class),
            rs.getObject("effective_to", LocalDate.class),
            rs.getObject("created_at", OffsetDateTime.class)
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
            ptm.hsn_code,
            ptm.effective_from,
            ptm.effective_to,
            ptm.created_at
        FROM product_tax_mapping ptm
        JOIN products p
            ON p.id = ptm.product_id
        JOIN tax_rates tr
            ON tr.id = ptm.tax_rate_id
        """;

    @Override
    public Optional<Long> lockActiveProduct(UUID productUuid) {

        String sql = """
            SELECT id
            FROM products
            WHERE uuid = ?
              AND active = TRUE
            FOR UPDATE
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getLong("id"),
            productUuid
        ).stream().findFirst();
    }


	@Override
	public boolean existsOverlappingMapping(
	    Long productId,
	    LocalDate effectiveFrom,
	    LocalDate effectiveTo
	) {
	
	    String sql = """
	        SELECT EXISTS (
	            SELECT 1
	            FROM product_tax_mapping
	            WHERE product_id = ?
	              AND (
	                  effective_to IS NULL
	                  OR effective_to > CAST(? AS DATE)
	              )
	              AND (
	                  CAST(? AS DATE) IS NULL
	                  OR effective_from < CAST(? AS DATE)
	              )
	        )
	        """;
	
	    Boolean exists = jdbcTemplate.queryForObject(
	        sql,
	        Boolean.class,
	        productId,
	        effectiveFrom,
	        effectiveTo,
	        effectiveTo
	    );
	
	    return Boolean.TRUE.equals(exists);
	}


    @Override
    public ProductTaxMapping create(
        UUID uuid,
        Long productId,
        Long taxRateId,
        String hsnCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {

        String sql = """
            INSERT INTO product_tax_mapping (
                uuid,
                product_id,
                tax_rate_id,
                hsn_code,
                effective_from,
                effective_to
            )
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING uuid
            """;

        UUID createdUuid = jdbcTemplate.queryForObject(
            sql,
            UUID.class,
            uuid,
            productId,
            taxRateId,
            hsnCode.trim(),
            effectiveFrom,
            effectiveTo
        );

        return findByUuid(createdUuid)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Created product tax mapping could not be retrieved"
                )
            );
    }

    @Override
    public Optional<ProductTaxMapping> findByUuid(UUID uuid) {

        String sql = SELECT_COLUMNS + """
            WHERE ptm.uuid = ?
            """;

        return jdbcTemplate.query(
            sql,
            rowMapper,
            uuid
        ).stream().findFirst();
    }

    @Override
    public List<ProductTaxMapping> findByProductUuid(
        UUID productUuid
    ) {

        String sql = SELECT_COLUMNS + """
            WHERE p.uuid = ?
            ORDER BY ptm.effective_from DESC
            """;

        return jdbcTemplate.query(
            sql,
            rowMapper,
            productUuid
        );
    }

    @Override
    public Optional<ProductTaxMapping> findCurrentByProductUuid(UUID productUuid) {

        String sql = """
            SELECT
                ptm.id,
                ptm.uuid,
                ptm.product_id,
                p.product_code,
                p.name AS product_name,
                tr.uuid AS tax_rate_uuid,
                tr.tax_name,
                ptm.hsn_code,
                ptm.effective_from,
                ptm.effective_to,
                ptm.created_at
            FROM product_tax_mapping ptm
            INNER JOIN products p
                ON p.id = ptm.product_id
            INNER JOIN tax_rates tr
                ON tr.id = ptm.tax_rate_id
            WHERE p.uuid = ?
              AND ptm.effective_from <= CURRENT_DATE
              AND (
                  ptm.effective_to IS NULL
                  OR ptm.effective_to > CURRENT_DATE
              )
            ORDER BY ptm.effective_from DESC
            LIMIT 1
            """;

        List<ProductTaxMapping> results = jdbcTemplate.query(
            sql,
            productTaxMappingRowMapper,
            productUuid
        );

        return results.stream().findFirst();
    }
    
    private final RowMapper<ProductTaxMapping> productTaxMappingRowMapper =
            (rs, rowNum) -> new ProductTaxMapping(
                    rs.getLong("id"),
                    rs.getObject("uuid", UUID.class),
                    rs.getLong("product_id"),
                    rs.getString("product_code"),
                    rs.getString("product_name"),
                    rs.getObject("tax_rate_uuid", UUID.class),
                    rs.getString("tax_name"),
                    rs.getString("hsn_code"),
                    rs.getObject("effective_from", LocalDate.class),
                    rs.getObject("effective_to", LocalDate.class),
                    rs.getObject("created_at", OffsetDateTime.class)
            );
    
    @Override
    public Optional<UUID> findProductUuidById(Long productId) {

        String sql = """
            SELECT uuid
            FROM products
            WHERE id = ?
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getObject("uuid", UUID.class),
            productId
        ).stream().findFirst();
    }
}
