package com.autoparts.erp.supplier.repositoryimpl;

import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.repository.SupplierRepository;
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
public class SupplierRepositoryImpl implements SupplierRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SupplierRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Supplier> ROW_MAPPER = (rs, rowNum) ->
        new Supplier(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("supplier_name"),
            rs.getString("contact_person"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("address"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getString("pincode"),
            rs.getString("gstin"),
            rs.getInt("payment_terms_days"),
            rs.getBoolean("active"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );

    private static final String SELECT_COLUMNS = """
        SELECT id, uuid, supplier_name, contact_person, phone,
               email, address, city, state, pincode, gstin,
               payment_terms_days, active, created_at, updated_at
        FROM suppliers
        """;

    @Override
    public Supplier create(Supplier supplier) {
        String sql = """
            INSERT INTO suppliers (
                uuid, supplier_name, contact_person, phone,
                email, address, city, state, pincode, gstin,
                payment_terms_days
            )
            VALUES (
                :uuid, :supplierName, :contactPerson, :phone,
                :email, :address, :city, :state, :pincode, :gstin,
                :paymentTermsDays
            )
            RETURNING id, uuid, supplier_name, contact_person, phone,
                      email, address, city, state, pincode, gstin,
                      payment_terms_days, active, created_at, updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", supplier.uuid())
                .addValue("supplierName", supplier.supplierName())
                .addValue("contactPerson", supplier.contactPerson())
                .addValue("phone", supplier.phone())
                .addValue("email", supplier.email())
                .addValue("address", supplier.address())
                .addValue("city", supplier.city())
                .addValue("state", supplier.state())
                .addValue("pincode", supplier.pincode())
                .addValue("gstin", supplier.gstin())
                .addValue("paymentTermsDays", supplier.paymentTermsDays());

        return jdbcTemplate.queryForObject(
            sql,
            params,
            ROW_MAPPER
        );
    }

    private static final Map<String, String> SUPPLIER_SORT_ALLOWLIST = Map.of(
            "id", "id",
            "supplierName", "supplier_name",
            "contactPerson", "contact_person",
            "phone", "phone",
            "city", "city",
            "createdAt", "created_at"
    );

    @Override
    public List<Supplier> findAll() {
        return jdbcTemplate.query(
            SELECT_COLUMNS + " ORDER BY supplier_name, id ASC LIMIT 501",
            new MapSqlParameterSource(),
            ROW_MAPPER
        );
    }

    @Override
    public List<Supplier> findPaged(
            int offset,
            int limit,
            String search,
            String city,
            String state,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE active = TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applySupplierFilters(sql, params, search, city, state);

        String sortColumn = SUPPLIER_SORT_ALLOWLIST.getOrDefault(sortBy, "supplier_name");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"id".equals(sortColumn)) {
            sql.append(", id ASC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
    }

    @Override
    public long countPaged(String search, String city, String state) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM suppliers WHERE active = TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applySupplierFilters(sql, params, search, city, state);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applySupplierFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String city,
            String state
    ) {
        if (city != null && !city.isBlank()) {
            sql.append(" AND LOWER(city) = LOWER(:city) ");
            params.addValue("city", city.trim());
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND LOWER(state) = LOWER(:state) ");
            params.addValue("state", state.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append("""
                 AND (
                     LOWER(supplier_name) LIKE :searchPattern
                     OR LOWER(contact_person) LIKE :searchPattern
                     OR phone LIKE :searchPattern
                     OR gstin LIKE :searchPattern
                 )
            """);
            params.addValue("searchPattern", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    @Override
    public Optional<Supplier> findByUuid(UUID uuid) {
        List<Supplier> results = jdbcTemplate.query(
            SELECT_COLUMNS + " WHERE uuid = :uuid",
            new MapSqlParameterSource("uuid", uuid),
            ROW_MAPPER
        );

        return results.stream().findFirst();
    }

    @Override
    public boolean update(UUID uuid, Supplier supplier) {
        String sql = """
            UPDATE suppliers
            SET supplier_name = :supplierName,
                contact_person = :contactPerson,
                phone = :phone,
                email = :email,
                address = :address,
                city = :city,
                state = :state,
                pincode = :pincode,
                gstin = :gstin,
                payment_terms_days = :paymentTermsDays,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
              AND active = TRUE
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("supplierName", supplier.supplierName())
                .addValue("contactPerson", supplier.contactPerson())
                .addValue("phone", supplier.phone())
                .addValue("email", supplier.email())
                .addValue("address", supplier.address())
                .addValue("city", supplier.city())
                .addValue("state", supplier.state())
                .addValue("pincode", supplier.pincode())
                .addValue("gstin", supplier.gstin())
                .addValue("paymentTermsDays", supplier.paymentTermsDays())
                .addValue("uuid", uuid);

        return jdbcTemplate.update(sql, params) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {
        String sql = """
            UPDATE suppliers
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

    @Override
    public boolean existsBySupplierName(String supplierName) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE LOWER(TRIM(supplier_name)) = LOWER(TRIM(:supplierName))
            )
            """;

        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("supplierName", supplierName),
                Boolean.class
            )
        );
    }

    @Override
    public boolean existsByPhone(String phone) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE REGEXP_REPLACE(
                    TRIM(phone), '[[:space:]]+', '', 'g'
                ) = REGEXP_REPLACE(
                    TRIM(:phone), '[[:space:]]+', '', 'g'
                )
            )
            """;

        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("phone", phone),
                Boolean.class
            )
        );
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE email IS NOT NULL
                  AND TRIM(email) <> ''
                  AND LOWER(TRIM(email)) = LOWER(TRIM(:email))
            )
            """;

        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("email", email),
                Boolean.class
            )
        );
    }

    @Override
    public boolean existsByGstin(String gstin) {
        if (gstin == null || gstin.isBlank()) {
            return false;
        }

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE gstin IS NOT NULL
                  AND TRIM(gstin) <> ''
                  AND UPPER(TRIM(gstin)) = UPPER(TRIM(:gstin))
            )
            """;

        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("gstin", gstin),
                Boolean.class
            )
        );
    }
}
