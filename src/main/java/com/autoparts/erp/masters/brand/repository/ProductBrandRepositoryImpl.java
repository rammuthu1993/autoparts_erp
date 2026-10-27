package com.autoparts.erp.masters.brand.repository;

import com.autoparts.erp.masters.brand.model.ProductBrand;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductBrandRepositoryImpl
        implements ProductBrandRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductBrandRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProductBrand> rowMapper = (rs, rowNum) ->
            new ProductBrand(
                    rs.getLong("id"),
                    rs.getObject("uuid", UUID.class),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getBoolean("active"),
                    rs.getObject(
                            "created_at",
                            java.time.OffsetDateTime.class
                    ),
                    rs.getObject(
                            "updated_at",
                            java.time.OffsetDateTime.class
                    )
            );

    @Override
    public ProductBrand create(ProductBrand brand) {

        String sql = """
                INSERT INTO product_brands
                    (uuid, name, description)
                VALUES (?, ?, ?)
                RETURNING id, uuid, name, description,
                          active, created_at, updated_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                rowMapper,
                brand.uuid(),
                brand.name(),
                brand.description()
        );
    }

    @Override
    public List<ProductBrand> findAll() {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_brands
                ORDER BY id DESC
                """;

        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public Optional<ProductBrand> findByUuid(UUID uuid) {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_brands
                WHERE uuid = ?
                """;

        return jdbcTemplate.query(sql, rowMapper, uuid)
                .stream()
                .findFirst();
    }

    @Override
    public boolean update(
            UUID uuid,
            String name,
            String description
    ) {

        String sql = """
                UPDATE product_brands
                SET name = ?,
                    description = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = ?
                  AND active = TRUE
                """;

        return jdbcTemplate.update(
                sql,
                name,
                description,
                uuid
        ) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {

        String sql = """
                UPDATE product_brands
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = ?
                  AND active = TRUE
                """;

        return jdbcTemplate.update(sql, uuid) > 0;
    }
    
    @Override
    public Optional<ProductBrand> findByNameIgnoreCase(String name) {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_brands
                WHERE LOWER(name) = LOWER(?)
                """;

        List<ProductBrand> brands = jdbcTemplate.query(
                sql,
                rowMapper,
                name.trim()
        );

        return brands.stream().findFirst();
    }
}