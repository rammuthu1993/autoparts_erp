package com.autoparts.erp.product.repositoryimpl;

import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repository.ProductRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Product> rowMapper = (rs, rowNum) ->
            new Product(
                    rs.getLong("id"),
                    rs.getObject("uuid", UUID.class),
                    rs.getString("product_code"),
                    rs.getString("part_no"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getLong("brand_id"),
                    rs.getString("brand_name"),
                    rs.getBigDecimal("purchase_price"),
                    rs.getBigDecimal("selling_price"),
                    rs.getInt("qty"),
                    rs.getBigDecimal("minimum_stock"),
                    rs.getString("unit"),
                    rs.getString("barcode"),
                    rs.getString("barcode_type"),
                    rs.getBoolean("active"),
                    rs.getObject("created_at", java.time.OffsetDateTime.class),
                    rs.getObject("updated_at", java.time.OffsetDateTime.class)
            );

    private static final String SELECT_PRODUCT = """
            SELECT p.id, p.uuid, p.product_code, p.part_no,
                   p.name, p.description,
                   p.category_id, c.name AS category_name,
                   p.brand_id, b.name AS brand_name,
                   p.purchase_price, p.selling_price,
                   p.qty, p.minimum_stock, p.unit,
                   p.barcode, p.barcode_type,
                   p.active, p.created_at, p.updated_at
            FROM products p
            JOIN product_categories c ON c.id = p.category_id
            JOIN product_brands b ON b.id = p.brand_id
            """;

    @Override
    public long nextProductCodeNumber() {
        Long number = jdbcTemplate.queryForObject(
                "SELECT nextval('product_code_seq')", new MapSqlParameterSource(), Long.class);
        if (number == null) {
            throw new IllegalStateException("Could not generate product code number");
        }
        return number;
    }

    @Override
    public boolean categoryExistsAndActive(Long categoryId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM product_categories
                    WHERE id = :categoryId AND active = TRUE
                )
                """, new MapSqlParameterSource("categoryId", categoryId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean brandExistsAndActive(Long brandId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM product_brands
                    WHERE id = :brandId AND active = TRUE
                )
                """, new MapSqlParameterSource("brandId", brandId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Product create(
            UUID uuid,
            String productCode,
            String partNo,
            String name,
            String description,
            Long categoryId,
            Long brandId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal minimumStock,
            String unit,
            String barcode,
            String barcodeType
    ) {
        String sql = """
                INSERT INTO products (
                    uuid, product_code, part_no, name, description,
                    category_id, brand_id,
                    purchase_price, selling_price, minimum_stock, unit,
                    barcode, barcode_type
                )
                VALUES (
                    :uuid, :productCode, :partNo, :name, :description,
                    :categoryId, :brandId,
                    :purchasePrice, :sellingPrice, :minimumStock, :unit,
                    :barcode, :barcodeType
                )
                RETURNING id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("productCode", productCode)
                .addValue("partNo", partNo)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("categoryId", categoryId)
                .addValue("brandId", brandId)
                .addValue("purchasePrice", purchasePrice)
                .addValue("sellingPrice", sellingPrice)
                .addValue("minimumStock", minimumStock)
                .addValue("unit", unit)
                .addValue("barcode", barcode)
                .addValue("barcodeType", barcodeType);

        Long id = jdbcTemplate.queryForObject(
                sql,
                params,
                Long.class
        );

        if (id == null) {
            throw new IllegalStateException("Product was not created");
        }

        return findByUuid(uuid)
                .orElseThrow(() -> new IllegalStateException(
                        "Created product could not be retrieved"));
    }

    private static final Map<String, String> SORT_ALLOWLIST = Map.of(
            "id", "p.id",
            "name", "p.name",
            "productCode", "p.product_code",
            "partNo", "p.part_no",
            "sellingPrice", "p.selling_price",
            "purchasePrice", "p.purchase_price",
            "qty", "p.qty",
            "createdAt", "p.created_at"
    );

    @Override
    public List<Product> findAll() {
        return jdbcTemplate.query(
                SELECT_PRODUCT + " ORDER BY p.id DESC",
                new MapSqlParameterSource(),
                rowMapper
        );
    }

    @Override
    public List<Product> findPaged(
            int offset,
            int limit,
            String search,
            Long categoryId,
            Long brandId,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(SELECT_PRODUCT).append(" WHERE p.active = TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyProductFilters(sql, params, search, categoryId, brandId);

        String sortColumn = SORT_ALLOWLIST.getOrDefault(sortBy, "p.id");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"p.id".equals(sortColumn)) {
            sql.append(", p.id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public long countPaged(String search, Long categoryId, Long brandId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p WHERE p.active = TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyProductFilters(sql, params, search, categoryId, brandId);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyProductFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            Long categoryId,
            Long brandId
    ) {
        if (categoryId != null) {
            sql.append(" AND p.category_id = :categoryId ");
            params.addValue("categoryId", categoryId);
        }
        if (brandId != null) {
            sql.append(" AND p.brand_id = :brandId ");
            params.addValue("brandId", brandId);
        }
        if (search != null && !search.isBlank()) {
            sql.append("""
                 AND (
                     LOWER(p.name) LIKE :searchPattern
                     OR LOWER(p.part_no) LIKE :searchPattern
                     OR LOWER(p.product_code) LIKE :searchPattern
                     OR p.barcode = :exactSearch
                 )
            """);
            String trimmed = search.trim();
            params.addValue("searchPattern", "%" + trimmed.toLowerCase(Locale.ROOT) + "%");
            params.addValue("exactSearch", trimmed);
        }
    }

    @Override
    public Optional<Product> findByUuid(UUID uuid) {
        return jdbcTemplate.query(
                SELECT_PRODUCT + " WHERE p.uuid = :uuid",
                new MapSqlParameterSource("uuid", uuid),
                rowMapper
        ).stream().findFirst();
    }

    @Override
    public boolean update(
            UUID uuid,
            String partNo,
            String name,
            String description,
            Long categoryId,
            Long brandId,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal minimumStock,
            String unit
    ) {
        String sql = """
                UPDATE products
                SET part_no = :partNo,
                    name = :name,
                    description = :description,
                    category_id = :categoryId,
                    brand_id = :brandId,
                    purchase_price = :purchasePrice,
                    selling_price = :sellingPrice,
                    minimum_stock = :minimumStock,
                    unit = :unit,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = TRUE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("partNo", partNo)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("categoryId", categoryId)
                .addValue("brandId", brandId)
                .addValue("purchasePrice", purchasePrice)
                .addValue("sellingPrice", sellingPrice)
                .addValue("minimumStock", minimumStock)
                .addValue("unit", unit)
                .addValue("uuid", uuid);

        return jdbcTemplate.update(sql, params) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {
        return jdbcTemplate.update("""
                UPDATE products
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                  AND active = TRUE
                """, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public boolean existsByName(String name) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM products WHERE LOWER(name) = LOWER(:name)
                )
                """, new MapSqlParameterSource("name", name), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByPartNo(String partNo) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM products WHERE part_no = :partNo
                )
                """, new MapSqlParameterSource("partNo", partNo), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByProductCode(String productCode) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM products WHERE product_code = :productCode
                )
                """, new MapSqlParameterSource("productCode", productCode), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByNameExcludingUuid(String name, UUID uuid) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("uuid", uuid);

        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM products
                    WHERE LOWER(name) = LOWER(:name)
                      AND uuid <> :uuid
                )
                """, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByPartNoExcludingUuid(String partNo, UUID uuid) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("partNo", partNo)
                .addValue("uuid", uuid);

        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM products
                    WHERE part_no = :partNo
                      AND uuid <> :uuid
                )
                """, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
