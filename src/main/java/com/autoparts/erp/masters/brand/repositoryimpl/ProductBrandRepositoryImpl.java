package com.autoparts.erp.masters.brand.repositoryimpl;

import com.autoparts.erp.masters.brand.dto.BrandUsageResponse;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repository.ProductBrandRepository;
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
public class ProductBrandRepositoryImpl
        implements ProductBrandRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductBrandRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProductBrand> rowMapper = (rs, rowNum) ->
            new ProductBrand(
                    rs.getLong("id"),
                    rs.getObject("uuid", UUID.class),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("country_of_origin"),
                    rs.getString("website"),
                    rs.getLong("product_count"),
                    rs.getLong("active_product_count"),
                    rs.getBoolean("active"),
                    rs.getObject("created_at", java.time.OffsetDateTime.class),
                    rs.getObject("updated_at", java.time.OffsetDateTime.class)
            );

    private static final String BASE_SELECT = """
            SELECT b.id,
                   b.uuid,
                   b.code,
                   b.name,
                   b.description,
                   b.country_of_origin,
                   b.website,
                   COALESCE(pb.product_count, 0) AS product_count,
                   COALESCE(pb.active_product_count, 0) AS active_product_count,
                   b.active,
                   b.created_at,
                   b.updated_at
            FROM product_brands b
            LEFT JOIN (
                SELECT brand_id,
                       COUNT(*) AS product_count,
                       COUNT(*) FILTER (WHERE active = TRUE) AS active_product_count
                FROM products
                GROUP BY brand_id
            ) pb ON pb.brand_id = b.id
            """;

    private static final Map<String, String> BRAND_SORT_ALLOWLIST = Map.of(
            "id", "b.id",
            "code", "b.code",
            "name", "b.name",
            "countryOfOrigin", "b.country_of_origin",
            "active", "b.active",
            "createdAt", "b.created_at",
            "updatedAt", "b.updated_at",
            "productCount", "COALESCE(pb.product_count, 0)"
    );

    @Override
    public ProductBrand create(ProductBrand brand) {
        String sql = """
                INSERT INTO product_brands
                    (uuid, code, name, description, country_of_origin, website)
                VALUES (:uuid, :code, :name, :description, :countryOfOrigin, :website)
                RETURNING id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", brand.uuid())
                .addValue("code", brand.code())
                .addValue("name", brand.name())
                .addValue("description", brand.description())
                .addValue("countryOfOrigin", brand.countryOfOrigin())
                .addValue("website", brand.website());

        Long id = jdbcTemplate.queryForObject(sql, params, Long.class);
        return findById(id).orElseThrow();
    }

    @Override
    public List<ProductBrand> findAll() {
        String sql = BASE_SELECT + """
                ORDER BY b.id DESC
                LIMIT 501
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public List<ProductBrand> findAllActive() {
        String sql = BASE_SELECT + """
                WHERE b.active = TRUE
                ORDER BY LOWER(b.name) ASC, b.id ASC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    @Override
    public List<ProductBrand> findPaged(
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

        String sortColumn = BRAND_SORT_ALLOWLIST.getOrDefault(sortBy, "b.id");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"b.id".equals(sortColumn)) {
            sql.append(", b.id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public long countPaged(String search, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM product_brands b WHERE 1=1 ");
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
            sql.append(" AND (b.name ILIKE :search OR b.description ILIKE :search OR b.code ILIKE :search OR b.country_of_origin ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }

        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(normalizedStatus)) {
                sql.append(" AND b.active = TRUE");
            } else if ("INACTIVE".equals(normalizedStatus)) {
                sql.append(" AND b.active = FALSE");
            }
        }
    }

    @Override
    public Optional<ProductBrand> findByUuid(UUID uuid) {
        String sql = BASE_SELECT + " WHERE b.uuid = :uuid";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("uuid", uuid), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProductBrand> findById(Long id) {
        String sql = BASE_SELECT + " WHERE b.id = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public boolean update(
            UUID uuid,
            String code,
            String name,
            String description,
            String countryOfOrigin,
            String website
    ) {
        String sql = """
                UPDATE product_brands
                SET code = :code,
                    name = :name,
                    description = :description,
                    country_of_origin = :countryOfOrigin,
                    website = :website,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = TRUE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("countryOfOrigin", countryOfOrigin)
                .addValue("website", website)
                .addValue("uuid", uuid);

        return jdbcTemplate.update(sql, params) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {
        String sql = """
                UPDATE product_brands
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
                UPDATE product_brands
                SET active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = FALSE
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public Optional<ProductBrand> findByNameIgnoreCase(String name) {
        String sql = BASE_SELECT + " WHERE LOWER(b.name) = LOWER(:name)";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("name", name.trim()), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProductBrand> findByCodeIgnoreCase(String code) {
        String sql = BASE_SELECT + " WHERE b.code IS NOT NULL AND LOWER(b.code) = LOWER(:code)";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("code", code.trim()), rowMapper)
                .stream()
                .findFirst();
    }

    @Override
    public BrandUsageResponse getUsage(UUID uuid) {
        String sql = """
                SELECT b.uuid,
                       b.name,
                       b.active,
                       COUNT(p.id) AS total_products,
                       COUNT(p.id) FILTER (WHERE p.active = TRUE) AS active_products,
                       COUNT(p.id) FILTER (WHERE p.active = FALSE) AS inactive_products
                FROM product_brands b
                LEFT JOIN products p ON p.brand_id = b.id
                WHERE b.uuid = :uuid
                GROUP BY b.id, b.uuid, b.name, b.active
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("uuid", uuid),
                (rs, rowNum) -> new BrandUsageResponse(
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
