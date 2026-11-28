package com.autoparts.erp.supplier.repositoryimpl;

import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.repository.SupplierRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
            rs.getString("supplier_code"),
            rs.getString("supplier_name"),
            rs.getString("supplier_type"),
            rs.getString("contact_person"),
            rs.getString("phone"),
            rs.getString("alternate_phone"),
            rs.getString("email"),
            rs.getString("address"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getString("pincode"),
            rs.getString("gstin"),
            rs.getString("gst_registration_type"),
            rs.getInt("payment_terms_days"),
            rs.getBigDecimal("credit_limit"),
            rs.getBigDecimal("outstanding_balance") != null
                ? rs.getBigDecimal("outstanding_balance")
                : BigDecimal.ZERO,
            rs.getBoolean("active"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );

    private static final String SELECT_COLUMNS = """
        SELECT s.id, s.uuid, s.supplier_code, s.supplier_name, s.supplier_type,
               s.contact_person, s.phone, s.alternate_phone,
               s.email, s.address, s.city, s.state, s.pincode, s.gstin,
               s.gst_registration_type, s.payment_terms_days, s.credit_limit,
               0::NUMERIC(14,2) AS outstanding_balance,
               s.active, s.created_at, s.updated_at
        FROM suppliers s
        """;

    private static final String SELECT_WITH_BALANCE = """
        SELECT s.id, s.uuid, s.supplier_code, s.supplier_name, s.supplier_type,
               s.contact_person, s.phone, s.alternate_phone,
               s.email, s.address, s.city, s.state, s.pincode, s.gstin,
               s.gst_registration_type, s.payment_terms_days, s.credit_limit,
               COALESCE(bal.outstanding_balance, 0) AS outstanding_balance,
               s.active, s.created_at, s.updated_at
        FROM suppliers s
        LEFT JOIN LATERAL (
            SELECT COALESCE((
                SELECT SUM(p.grand_total)
                FROM purchases p
                WHERE p.supplier_id = s.id
                  AND p.status = 'RECEIVED'
            ), 0)
            - COALESCE((
                SELECT SUM(pp.amount)
                FROM purchase_payments pp
                JOIN purchases p ON p.id = pp.purchase_id
                WHERE p.supplier_id = s.id
                  AND p.status = 'RECEIVED'
            ), 0)
            - COALESCE((
                SELECT SUM(pr.total_return_amount)
                FROM purchase_returns pr
                JOIN purchases p ON p.id = pr.purchase_id
                WHERE p.supplier_id = s.id
                  AND p.status = 'RECEIVED'
            ), 0) AS outstanding_balance
        ) bal ON TRUE
        """;

    private static final Map<String, String> SUPPLIER_SORT_ALLOWLIST = Map.of(
            "id", "s.id",
            "supplierCode", "s.supplier_code",
            "supplierName", "s.supplier_name",
            "contactPerson", "s.contact_person",
            "phone", "s.phone",
            "city", "s.city",
            "supplierType", "s.supplier_type",
            "active", "s.active",
            "createdAt", "s.created_at",
            "updatedAt", "s.updated_at"
    );

    @Override
    public Supplier create(Supplier supplier) {
        String sql = """
            INSERT INTO suppliers (
                uuid, supplier_code, supplier_name, supplier_type,
                contact_person, phone, alternate_phone,
                email, address, city, state, pincode, gstin,
                gst_registration_type, payment_terms_days, credit_limit
            )
            VALUES (
                :uuid,
                'SUP' || LPAD(nextval('supplier_code_seq')::TEXT, 6, '0'),
                :supplierName, :supplierType,
                :contactPerson, :phone, :alternatePhone,
                :email, :address, :city, :state, :pincode, :gstin,
                :gstRegistrationType, :paymentTermsDays, :creditLimit
            )
            RETURNING *, 0::NUMERIC(14,2) AS outstanding_balance
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", supplier.uuid())
                .addValue("supplierName", supplier.supplierName())
                .addValue("supplierType", supplier.supplierType() != null ? supplier.supplierType() : "DISTRIBUTOR")
                .addValue("contactPerson", supplier.contactPerson())
                .addValue("phone", supplier.phone())
                .addValue("alternatePhone", supplier.alternatePhone())
                .addValue("email", supplier.email())
                .addValue("address", supplier.address())
                .addValue("city", supplier.city())
                .addValue("state", supplier.state())
                .addValue("pincode", supplier.pincode())
                .addValue("gstin", supplier.gstin())
                .addValue("gstRegistrationType", supplier.gstRegistrationType())
                .addValue("paymentTermsDays", supplier.paymentTermsDays())
                .addValue("creditLimit", supplier.creditLimit());

        return jdbcTemplate.queryForObject(sql, params, ROW_MAPPER);
    }

    @Override
    public List<Supplier> findAll() {
        return jdbcTemplate.query(
            SELECT_COLUMNS + " ORDER BY s.supplier_name, s.id ASC LIMIT 501",
            new MapSqlParameterSource(),
            ROW_MAPPER
        );
    }

    @Override
    public List<Supplier> findAllActive() {
        return jdbcTemplate.query(
            SELECT_COLUMNS + " WHERE s.active = TRUE ORDER BY LOWER(s.supplier_name) ASC, s.id ASC",
            new MapSqlParameterSource(),
            ROW_MAPPER
        );
    }

    @Override
    public List<Supplier> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String city,
            String state,
            String supplierType,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_BALANCE).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applySupplierFilters(sql, params, search, status, city, state, supplierType);

        String sortColumn = SUPPLIER_SORT_ALLOWLIST.getOrDefault(sortBy, "s.supplier_name");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"s.id".equals(sortColumn)) {
            sql.append(", s.id ASC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
    }

    @Override
    public long countPaged(
            String search,
            String status,
            String city,
            String state,
            String supplierType
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM suppliers s WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applySupplierFilters(sql, params, search, status, city, state, supplierType);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applySupplierFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String status,
            String city,
            String state,
            String supplierType
    ) {
        if (status != null && !status.isBlank()) {
            String upper = status.trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(upper)) {
                sql.append(" AND s.active = TRUE ");
            } else if ("INACTIVE".equals(upper)) {
                sql.append(" AND s.active = FALSE ");
            }
        }
        if (city != null && !city.isBlank()) {
            sql.append(" AND LOWER(s.city) = LOWER(:city) ");
            params.addValue("city", city.trim());
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND LOWER(s.state) = LOWER(:state) ");
            params.addValue("state", state.trim());
        }
        if (supplierType != null && !supplierType.isBlank() && !"ALL".equalsIgnoreCase(supplierType.trim())) {
            sql.append(" AND UPPER(s.supplier_type) = UPPER(:supplierType) ");
            params.addValue("supplierType", supplierType.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append("""
                 AND (
                     LOWER(s.supplier_name) LIKE :searchPattern
                     OR LOWER(COALESCE(s.supplier_code, '')) LIKE :searchPattern
                     OR LOWER(COALESCE(s.contact_person, '')) LIKE :searchPattern
                     OR LOWER(COALESCE(s.phone, '')) LIKE :searchPattern
                     OR LOWER(COALESCE(s.email, '')) LIKE :searchPattern
                     OR LOWER(COALESCE(s.gstin, '')) LIKE :searchPattern
                 )
            """);
            params.addValue("searchPattern", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    @Override
    public Optional<Supplier> findByUuid(UUID uuid) {
        List<Supplier> results = jdbcTemplate.query(
            SELECT_WITH_BALANCE + " WHERE s.uuid = :uuid",
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
                supplier_type = :supplierType,
                contact_person = :contactPerson,
                phone = :phone,
                alternate_phone = :alternatePhone,
                email = :email,
                address = :address,
                city = :city,
                state = :state,
                pincode = :pincode,
                gstin = :gstin,
                gst_registration_type = :gstRegistrationType,
                payment_terms_days = :paymentTermsDays,
                credit_limit = :creditLimit,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
              AND active = TRUE
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("supplierName", supplier.supplierName())
                .addValue("supplierType", supplier.supplierType() != null ? supplier.supplierType() : "DISTRIBUTOR")
                .addValue("contactPerson", supplier.contactPerson())
                .addValue("phone", supplier.phone())
                .addValue("alternatePhone", supplier.alternatePhone())
                .addValue("email", supplier.email())
                .addValue("address", supplier.address())
                .addValue("city", supplier.city())
                .addValue("state", supplier.state())
                .addValue("pincode", supplier.pincode())
                .addValue("gstin", supplier.gstin())
                .addValue("gstRegistrationType", supplier.gstRegistrationType())
                .addValue("paymentTermsDays", supplier.paymentTermsDays())
                .addValue("creditLimit", supplier.creditLimit())
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
        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public boolean reactivate(UUID uuid) {
        String sql = """
            UPDATE suppliers
            SET active = TRUE,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = :uuid
              AND active = FALSE
            """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("uuid", uuid)) > 0;
    }

    @Override
    public boolean existsBySupplierName(String supplierName) {
        return existsBySupplierNameExcludingUuid(supplierName, null);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return existsByPhoneExcludingUuid(phone, null);
    }

    @Override
    public boolean existsByEmail(String email) {
        return existsByEmailExcludingUuid(email, null);
    }

    @Override
    public boolean existsByGstin(String gstin) {
        return existsByGstinExcludingUuid(gstin, null);
    }

    @Override
    public boolean existsBySupplierNameExcludingUuid(String supplierName, UUID uuid) {
        if (supplierName == null || supplierName.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE LOWER(TRIM(supplier_name)) = LOWER(TRIM(:supplierName))
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("supplierName", supplierName);
        if (uuid != null) {
            sql.append(" AND uuid <> :excludeUuid ");
            params.addValue("excludeUuid", uuid);
        }
        sql.append(")");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql.toString(), params, Boolean.class));
    }

    @Override
    public boolean existsByPhoneExcludingUuid(String phone, UUID uuid) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE REGEXP_REPLACE(TRIM(phone), '[[:space:]]+', '', 'g') = REGEXP_REPLACE(TRIM(:phone), '[[:space:]]+', '', 'g')
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("phone", phone);
        if (uuid != null) {
            sql.append(" AND uuid <> :excludeUuid ");
            params.addValue("excludeUuid", uuid);
        }
        sql.append(")");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql.toString(), params, Boolean.class));
    }

    @Override
    public boolean existsByEmailExcludingUuid(String email, UUID uuid) {
        if (email == null || email.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE email IS NOT NULL
                  AND TRIM(email) <> ''
                  AND LOWER(TRIM(email)) = LOWER(TRIM(:email))
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("email", email);
        if (uuid != null) {
            sql.append(" AND uuid <> :excludeUuid ");
            params.addValue("excludeUuid", uuid);
        }
        sql.append(")");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql.toString(), params, Boolean.class));
    }

    @Override
    public boolean existsByGstinExcludingUuid(String gstin, UUID uuid) {
        if (gstin == null || gstin.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
            SELECT EXISTS (
                SELECT 1
                FROM suppliers
                WHERE gstin IS NOT NULL
                  AND TRIM(gstin) <> ''
                  AND UPPER(TRIM(gstin)) = UPPER(TRIM(:gstin))
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("gstin", gstin);
        if (uuid != null) {
            sql.append(" AND uuid <> :excludeUuid ");
            params.addValue("excludeUuid", uuid);
        }
        sql.append(")");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql.toString(), params, Boolean.class));
    }

    @Override
    public SupplierActivityStats getActivityStats(Long supplierId) {
        String sql = """
                SELECT
                    (SELECT COUNT(*)
                     FROM purchases p
                     WHERE p.supplier_id = :supplierId
                       AND p.status = 'RECEIVED') AS total_purchases_count,
                    (SELECT MAX(p.purchase_date)
                     FROM purchases p
                     WHERE p.supplier_id = :supplierId
                       AND p.status = 'RECEIVED') AS last_purchase_date,
                    (SELECT MAX(CAST(pp.payment_date AS DATE))
                     FROM purchase_payments pp
                     JOIN purchases p ON p.id = pp.purchase_id
                     WHERE p.supplier_id = :supplierId
                       AND p.status = 'RECEIVED') AS last_payment_date
                """;
        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("supplierId", supplierId),
                (rs, rowNum) -> new SupplierActivityStats(
                        rs.getLong("total_purchases_count"),
                        rs.getObject("last_purchase_date", LocalDate.class),
                        rs.getObject("last_payment_date", LocalDate.class)
                )
        );
    }
}
