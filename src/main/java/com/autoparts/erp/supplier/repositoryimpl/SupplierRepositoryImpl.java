package com.autoparts.erp.supplier.repositoryimpl;

import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.repository.SupplierRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Override
    public List<Supplier> findAll() {
        return jdbcTemplate.query(
            SELECT_COLUMNS + " ORDER BY supplier_name",
            new MapSqlParameterSource(),
            ROW_MAPPER
        );
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
