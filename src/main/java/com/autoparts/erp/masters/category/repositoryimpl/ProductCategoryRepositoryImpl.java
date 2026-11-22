package com.autoparts.erp.masters.category.repositoryimpl;

import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repository.ProductCategoryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductCategoryRepositoryImpl
        implements ProductCategoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductCategoryRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
                VALUES (:uuid, :name, :description)
                RETURNING id, uuid, name, description,
                          active, created_at, updated_at
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", category.uuid())
                .addValue("name", category.name())
                .addValue("description", category.description());

        return jdbcTemplate.queryForObject(
                sql,
                params,
                rowMapper
        );
    }

    private static final Map<String, String> CATEGORY_SORT_ALLOWLIST = Map.of(
            "id", "id",
            "name", "name",
            "createdAt", "created_at"
    );

    @Override
    public List<ProductCategory> findAll() {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_categories
                ORDER BY id DESC
                LIMIT 501
                """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public List<ProductCategory> findPaged(
            int offset,
            int limit,
            String search,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_categories
                WHERE 1=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (name ILIKE :search OR description ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }

        String sortColumn = CATEGORY_SORT_ALLOWLIST.getOrDefault(sortBy, "id");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"id".equals(sortColumn)) {
            sql.append(", id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public long countPaged(String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM product_categories WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (name ILIKE :search OR description ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<ProductCategory> findByUuid(UUID uuid) {

        String sql = """
                SELECT id, uuid, name, description,
                       active, created_at, updated_at
                FROM product_categories
                WHERE uuid = :uuid
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("uuid", uuid),
                rowMapper
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
                SET name = :name,
                    description = :description,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = TRUE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("description", description)
                .addValue("uuid", uuid);

        return jdbcTemplate.update(sql, params) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {

        String sql = """
                UPDATE product_categories
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = TRUE
                """;

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource("uuid", uuid)
        ) > 0;
    }
}
