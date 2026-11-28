package com.autoparts.erp.masters.category.repositoryimpl;

import com.autoparts.erp.masters.category.dto.CategoryUsageResponse;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repository.ProductCategoryRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
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
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getObject("parent_id", Long.class),
                    rs.getObject("parent_uuid", UUID.class),
                    rs.getString("parent_name"),
                    rs.getLong("product_count"),
                    rs.getLong("active_product_count"),
                    rs.getBoolean("active"),
                    rs.getObject("created_at", java.time.OffsetDateTime.class),
                    rs.getObject("updated_at", java.time.OffsetDateTime.class)
            );

    private static final String BASE_SELECT = """
            SELECT c.id,
                   c.uuid,
                   c.code,
                   c.name,
                   c.description,
                   c.parent_id,
                   parent.uuid AS parent_uuid,
                   parent.name AS parent_name,
                   COALESCE(pc.product_count, 0) AS product_count,
                   COALESCE(pc.active_product_count, 0) AS active_product_count,
                   c.active,
                   c.created_at,
                   c.updated_at
            FROM product_categories c
            LEFT JOIN product_categories parent ON parent.id = c.parent_id
            LEFT JOIN (
                SELECT category_id,
                       COUNT(*) AS product_count,
                       COUNT(*) FILTER (WHERE active = TRUE) AS active_product_count
                FROM products
                GROUP BY category_id
            ) pc ON pc.category_id = c.id
            """;

    private static final Map<String, String> CATEGORY_SORT_ALLOWLIST = Map.of(
            "id", "c.id",
            "code", "c.code",
            "name", "c.name",
            "active", "c.active",
            "createdAt", "c.created_at",
            "updatedAt", "c.updated_at",
            "productCount", "COALESCE(pc.product_count, 0)"
    );

    @Override
    public ProductCategory create(ProductCategory category) {
        String sql = """
                INSERT INTO product_categories
                    (uuid, code, name, description, parent_id)
                VALUES (:uuid, :code, :name, :description, :parentId)
                RETURNING id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", category.uuid())
                .addValue("code", category.code())
                .addValue("name", category.name())
                .addValue("description", category.description())
                .addValue("parentId", category.parentId());

        Long id = jdbcTemplate.queryForObject(sql, params, Long.class);
        return findById(id).orElseThrow();
    }

    @Override
    public List<ProductCategory> findAll() {
        String sql = BASE_SELECT + """
                ORDER BY c.id DESC
                LIMIT 501
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public List<ProductCategory> findAllActive() {
        String sql = BASE_SELECT + """
                WHERE c.active = TRUE
                ORDER BY LOWER(c.name) ASC, c.id ASC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public List<ProductCategory> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        appendFilters(sql, params, search, status);

        String sortColumn = CATEGORY_SORT_ALLOWLIST.getOrDefault(sortBy, "c.id");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"c.id".equals(sortColumn)) {
            sql.append(", c.id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public long countPaged(String search, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM product_categories c WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        appendFilters(sql, params, search, status);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void appendFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String status
    ) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND (c.name ILIKE :search OR c.description ILIKE :search OR c.code ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }

        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(normalizedStatus)) {
                sql.append(" AND c.active = TRUE");
            } else if ("INACTIVE".equals(normalizedStatus)) {
                sql.append(" AND c.active = FALSE");
            }
        }
    }

    @Override
    public Optional<ProductCategory> findByUuid(UUID uuid) {
        String sql = BASE_SELECT + " WHERE c.uuid = :uuid";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("uuid", uuid), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProductCategory> findById(Long id) {
        String sql = BASE_SELECT + " WHERE c.id = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProductCategory> findByNameIgnoreCase(String name) {
        String sql = BASE_SELECT + " WHERE LOWER(c.name) = LOWER(:name)";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("name", name.trim()), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProductCategory> findByCodeIgnoreCase(String code) {
        String sql = BASE_SELECT + " WHERE c.code IS NOT NULL AND LOWER(c.code) = LOWER(:code)";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("code", code.trim()), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public boolean update(
            UUID uuid,
            String code,
            String name,
            String description,
            Long parentId
    ) {
        String sql = """
                UPDATE product_categories
                SET code = :code,
                    name = :name,
                    description = :description,
                    parent_id = :parentId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = TRUE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("parentId", parentId)
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
        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public boolean reactivate(UUID uuid) {
        String sql = """
                UPDATE product_categories
                SET active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = FALSE
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public CategoryUsageResponse getUsage(UUID uuid) {
        String sql = """
                SELECT c.uuid,
                       c.name,
                       c.active,
                       COUNT(p.id) AS total_products,
                       COUNT(p.id) FILTER (WHERE p.active = TRUE) AS active_products,
                       COUNT(p.id) FILTER (WHERE p.active = FALSE) AS inactive_products
                FROM product_categories c
                LEFT JOIN products p ON p.category_id = c.id
                WHERE c.uuid = :uuid
                GROUP BY c.id, c.uuid, c.name, c.active
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("uuid", uuid),
                (rs, rowNum) -> new CategoryUsageResponse(
                        rs.getObject("uuid", UUID.class),
                        rs.getString("name"),
                        rs.getBoolean("active"),
                        rs.getLong("total_products"),
                        rs.getLong("active_products"),
                        rs.getLong("inactive_products")
                )
        ).stream().findFirst().orElse(null);
    }
}
