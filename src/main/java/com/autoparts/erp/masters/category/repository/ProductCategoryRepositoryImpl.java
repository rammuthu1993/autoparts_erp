package com.autoparts.erp.masters.category.repository;

import com.autoparts.erp.masters.category.model.ProductCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductCategoryRepositoryImpl
        implements ProductCategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductCategoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProductCategory> rowMapper = (rs, rowNum) ->
            new ProductCategory(
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
    public ProductCategory create(ProductCategory category) {

        String sql = """
                INSERT INTO product_categories
                    (uuid, name, description)
                VALUES (?, ?, ?)
                RETURNING id, uuid, name, description,
                          active, created_at, updated_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                rowMapper,
                category.uuid(),
                category.name(),
                category.description()
        );
    }

    @Override
    public List<ProductCategory> findAll() {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_categories
                ORDER BY id DESC
                """;

        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public Optional<ProductCategory> findByUuid(UUID uuid) {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_categories
                WHERE uuid = ?
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                uuid
        ).stream().findFirst();
    }

    @Override
    public boolean update(
            UUID uuid,
            String name,
            String description
    ) {
        String sql = """
                UPDATE product_categories
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
                UPDATE product_categories
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = ?
                  AND active = TRUE
                """;

        return jdbcTemplate.update(sql, uuid) > 0;
    }
}