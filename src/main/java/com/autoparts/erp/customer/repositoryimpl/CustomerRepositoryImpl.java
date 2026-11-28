package com.autoparts.erp.customer.repositoryimpl;

import com.autoparts.erp.customer.dto.CustomerDuplicateWarning;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repository.CustomerRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerRepositoryImpl implements CustomerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Customer> ROW_MAPPER = (rs, rowNum) ->
            new Customer(
                    rs.getLong("id"),
                    rs.getObject("uuid", UUID.class),
                    rs.getString("customer_code"),
                    rs.getString("customer_name"),
                    rs.getString("customer_type"),
                    rs.getString("contact_person"),
                    rs.getString("phone"),
                    rs.getString("alternate_phone"),
                    rs.getString("email"),
                    rs.getString("address"),
                    rs.getString("shipping_address"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getString("pincode"),
                    rs.getString("gstin"),
                    rs.getString("gst_registration_type"),
                    rs.getBigDecimal("credit_limit"),
                    rs.getObject("credit_period_days", Integer.class),
                    rs.getBigDecimal("outstanding_balance") != null
                            ? rs.getBigDecimal("outstanding_balance")
                            : BigDecimal.ZERO,
                    rs.getBoolean("active"),
                    rs.getObject("created_at", java.time.OffsetDateTime.class),
                    rs.getObject("updated_at", java.time.OffsetDateTime.class)
            );

    private static final String SELECT_COLUMNS = """
            SELECT c.id, c.uuid, c.customer_code, c.customer_name,
                   c.customer_type, c.contact_person,
                   c.phone, c.alternate_phone, c.email,
                   c.address, c.shipping_address, c.city, c.state,
                   c.pincode, c.gstin, c.gst_registration_type,
                   c.credit_limit, c.credit_period_days,
                   0::NUMERIC(14,2) AS outstanding_balance,
                   c.active, c.created_at, c.updated_at
            FROM customers c
            """;

    private static final String SELECT_WITH_BALANCE = """
            SELECT c.id, c.uuid, c.customer_code, c.customer_name,
                   c.customer_type, c.contact_person,
                   c.phone, c.alternate_phone, c.email,
                   c.address, c.shipping_address, c.city, c.state,
                   c.pincode, c.gstin, c.gst_registration_type,
                   c.credit_limit, c.credit_period_days,
                   COALESCE(bal.outstanding_balance, 0) AS outstanding_balance,
                   c.active, c.created_at, c.updated_at
            FROM customers c
            LEFT JOIN LATERAL (
                SELECT COALESCE((
                    SELECT SUM(s.grand_total)
                    FROM sales s
                    WHERE s.customer_id = c.id
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                ), 0)
                - COALESCE((
                    SELECT SUM(sp.amount)
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE s.customer_id = c.id
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                ), 0)
                - COALESCE((
                    SELECT SUM(sr.total_return_amount)
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = c.id
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                      AND sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                ), 0)
                + COALESCE((
                    SELECT SUM(sf.amount)
                    FROM sales_refunds sf
                    JOIN sales_returns sr ON sr.id = sf.sales_return_id
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id = c.id
                      AND (s.status = 'COMPLETED'
                           OR (s.status = 'CANCELLED' AND EXISTS (
                               SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                           )))
                ), 0)
                - COALESCE((
                    SELECT SUM(sic.cancelled_invoice_amount)
                    FROM sales_invoice_cancellations sic
                    JOIN sales s ON s.id = sic.sale_id
                    WHERE s.customer_id = c.id
                      AND s.status = 'CANCELLED'
                ), 0)
                + COALESCE((
                    SELECT SUM(scr.amount)
                    FROM sales_cancellation_refunds scr
                    JOIN sales s ON s.id = scr.sale_id
                    WHERE s.customer_id = c.id
                      AND s.status = 'CANCELLED'
                      AND scr.status NOT IN ('FAILED', 'REVERSED')
                ), 0) AS outstanding_balance
            ) bal ON TRUE
            """;

    private static final Map<String, String> CUSTOMER_SORT_ALLOWLIST = Map.of(
            "id", "c.id",
            "customerName", "c.customer_name",
            "customerCode", "c.customer_code",
            "phone", "c.phone",
            "city", "c.city",
            "customerType", "c.customer_type",
            "active", "c.active",
            "createdAt", "c.created_at",
            "updatedAt", "c.updated_at"
    );

    @Override
    public Customer create(Customer customer) {
        String sql = """
                INSERT INTO customers (
                    uuid,
                    customer_code,
                    customer_name,
                    customer_type,
                    contact_person,
                    phone,
                    alternate_phone,
                    email,
                    address,
                    shipping_address,
                    city,
                    state,
                    pincode,
                    gstin,
                    gst_registration_type,
                    credit_limit,
                    credit_period_days
                )
                VALUES (
                    :uuid,
                    'CUS' || LPAD(
                        nextval('customer_code_seq')::TEXT,
                        6,
                        '0'
                    ),
                    :customerName,
                    :customerType,
                    :contactPerson,
                    :phone,
                    :alternatePhone,
                    :email,
                    :address,
                    :shippingAddress,
                    :city,
                    :state,
                    :pincode,
                    :gstin,
                    :gstRegistrationType,
                    :creditLimit,
                    :creditPeriodDays
                )
                RETURNING *, 0::NUMERIC(14,2) AS outstanding_balance
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", customer.uuid())
                .addValue("customerName", customer.customerName())
                .addValue("customerType", customer.customerType() != null ? customer.customerType() : "RETAIL")
                .addValue("contactPerson", customer.contactPerson())
                .addValue("phone", customer.phone())
                .addValue("alternatePhone", customer.alternatePhone())
                .addValue("email", customer.email())
                .addValue("address", customer.address())
                .addValue("shippingAddress", customer.shippingAddress())
                .addValue("city", customer.city())
                .addValue("state", customer.state())
                .addValue("pincode", customer.pincode())
                .addValue("gstin", customer.gstin())
                .addValue("gstRegistrationType", customer.gstRegistrationType())
                .addValue("creditLimit", customer.creditLimit())
                .addValue("creditPeriodDays", customer.creditPeriodDays());

        return jdbcTemplate.queryForObject(sql, params, ROW_MAPPER);
    }

    @Override
    public List<Customer> findAll() {
        String sql = SELECT_COLUMNS + """
                WHERE c.active = TRUE
                ORDER BY c.customer_name, c.id ASC
                LIMIT 501
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), ROW_MAPPER);
    }

    @Override
    public List<Customer> findAllActive() {
        String sql = SELECT_COLUMNS + """
                WHERE c.active = TRUE
                ORDER BY LOWER(c.customer_name) ASC, c.id ASC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), ROW_MAPPER);
    }

    @Override
    public List<Customer> findPaged(
            int offset,
            int limit,
            String search,
            String status,
            String city,
            String state,
            String customerType,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_BALANCE).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyCustomerFilters(sql, params, search, status, city, state, customerType);

        String sortColumn = CUSTOMER_SORT_ALLOWLIST.getOrDefault(sortBy, "c.customer_name");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"c.id".equals(sortColumn)) {
            sql.append(", c.id ASC");
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
            String customerType
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM customers c WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyCustomerFilters(sql, params, search, status, city, state, customerType);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyCustomerFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String status,
            String city,
            String state,
            String customerType
    ) {
        if (status != null && !status.isBlank()) {
            String upper = status.trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(upper)) {
                sql.append(" AND c.active = TRUE ");
            } else if ("INACTIVE".equals(upper)) {
                sql.append(" AND c.active = FALSE ");
            }
        }
        if (city != null && !city.isBlank()) {
            sql.append(" AND LOWER(c.city) = LOWER(:city) ");
            params.addValue("city", city.trim());
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND LOWER(c.state) = LOWER(:state) ");
            params.addValue("state", state.trim());
        }
        if (customerType != null && !customerType.isBlank() && !"ALL".equalsIgnoreCase(customerType.trim())) {
            sql.append(" AND UPPER(c.customer_type) = UPPER(:customerType) ");
            params.addValue("customerType", customerType.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append("""
                 AND (
                     LOWER(c.customer_name) LIKE :searchPattern
                     OR LOWER(c.customer_code) LIKE :searchPattern
                     OR LOWER(COALESCE(c.phone, '')) LIKE :searchPattern
                     OR LOWER(COALESCE(c.email, '')) LIKE :searchPattern
                     OR LOWER(COALESCE(c.gstin, '')) LIKE :searchPattern
                 )
            """);
            params.addValue("searchPattern", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    @Override
    public Optional<Customer> findByUuid(UUID uuid) {
        String sql = SELECT_WITH_BALANCE + " WHERE c.uuid = :uuid";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("uuid", uuid), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Customer> findById(Long id) {
        String sql = SELECT_WITH_BALANCE + " WHERE c.id = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public boolean update(Customer customer) {
        String sql = """
                UPDATE customers
                SET customer_name = :customerName,
                    customer_type = :customerType,
                    contact_person = :contactPerson,
                    phone = :phone,
                    alternate_phone = :alternatePhone,
                    email = :email,
                    address = :address,
                    shipping_address = :shippingAddress,
                    city = :city,
                    state = :state,
                    pincode = :pincode,
                    gstin = :gstin,
                    gst_registration_type = :gstRegistrationType,
                    credit_limit = :creditLimit,
                    credit_period_days = :creditPeriodDays,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND active = TRUE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", customer.id())
                .addValue("customerName", customer.customerName())
                .addValue("customerType", customer.customerType() != null ? customer.customerType() : "RETAIL")
                .addValue("contactPerson", customer.contactPerson())
                .addValue("phone", customer.phone())
                .addValue("alternatePhone", customer.alternatePhone())
                .addValue("email", customer.email())
                .addValue("address", customer.address())
                .addValue("shippingAddress", customer.shippingAddress())
                .addValue("city", customer.city())
                .addValue("state", customer.state())
                .addValue("pincode", customer.pincode())
                .addValue("gstin", customer.gstin())
                .addValue("gstRegistrationType", customer.gstRegistrationType())
                .addValue("creditLimit", customer.creditLimit())
                .addValue("creditPeriodDays", customer.creditPeriodDays());

        return jdbcTemplate.update(sql, params) > 0;
    }

    @Override
    public boolean deactivate(Long id) {
        String sql = """
                UPDATE customers
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND active = TRUE
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("id", id)) > 0;
    }

    @Override
    public boolean reactivate(Long id) {
        String sql = """
                UPDATE customers
                SET active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND active = FALSE
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("id", id)) > 0;
    }

    @Override
    public boolean existsByGstinExcludingUuid(String gstin, UUID excludeUuid) {
        if (gstin == null || gstin.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
                SELECT EXISTS (
                    SELECT 1
                    FROM customers
                    WHERE gstin IS NOT NULL
                      AND TRIM(gstin) <> ''
                      AND UPPER(TRIM(gstin)) = UPPER(TRIM(:gstin))
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("gstin", gstin);
        if (excludeUuid != null) {
            sql.append(" AND uuid <> :excludeUuid ");
            params.addValue("excludeUuid", excludeUuid);
        }
        sql.append(")");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql.toString(), params, Boolean.class));
    }

    @Override
    public List<CustomerDuplicateWarning> findPotentialDuplicates(
            String phone,
            String email,
            String gstin,
            UUID excludeUuid
    ) {
        String cleanPhone = normalizePhone(phone);
        String cleanEmail = email != null && !email.isBlank() ? email.trim().toLowerCase(Locale.ROOT) : null;
        String cleanGstin = gstin != null && !gstin.isBlank() ? gstin.trim().toUpperCase(Locale.ROOT) : null;

        if (cleanPhone == null && cleanEmail == null && cleanGstin == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT uuid, customer_code, customer_name, phone, email, gstin, active
                FROM customers
                WHERE 1=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (excludeUuid != null) {
            sql.append(" AND uuid <> :excludeUuid ");
            params.addValue("excludeUuid", excludeUuid);
        }

        List<String> orClauses = new ArrayList<>();
        if (cleanPhone != null) {
            orClauses.add("REGEXP_REPLACE(COALESCE(phone, ''), '[[:space:]\\-()]', '', 'g') = :phone");
            params.addValue("phone", cleanPhone);
        }
        if (cleanEmail != null) {
            orClauses.add("LOWER(TRIM(COALESCE(email, ''))) = :email");
            params.addValue("email", cleanEmail);
        }
        if (cleanGstin != null) {
            orClauses.add("UPPER(TRIM(COALESCE(gstin, ''))) = :gstin");
            params.addValue("gstin", cleanGstin);
        }
        sql.append(" AND (").append(String.join(" OR ", orClauses)).append(") ORDER BY active DESC, id DESC LIMIT 10");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
            String rowPhone = rs.getString("phone");
            String rowEmail = rs.getString("email");
            String rowGstin = rs.getString("gstin");
            List<String> matched = new ArrayList<>();
            if (cleanPhone != null && cleanPhone.equals(normalizePhone(rowPhone))) {
                matched.add("phone");
            }
            if (cleanEmail != null && rowEmail != null && cleanEmail.equals(rowEmail.trim().toLowerCase(Locale.ROOT))) {
                matched.add("email");
            }
            if (cleanGstin != null && rowGstin != null && cleanGstin.equals(rowGstin.trim().toUpperCase(Locale.ROOT))) {
                matched.add("gstin");
            }
            return new CustomerDuplicateWarning(
                    rs.getObject("uuid", UUID.class),
                    rs.getString("customer_code"),
                    rs.getString("customer_name"),
                    rowPhone,
                    rowEmail,
                    rowGstin,
                    rs.getBoolean("active"),
                    matched
            );
        });
    }

    @Override
    public CustomerActivityStats getActivityStats(Long customerId) {
        String sql = """
                SELECT
                    (SELECT COUNT(*)
                     FROM sales s
                     WHERE s.customer_id = :customerId
                       AND (s.status = 'COMPLETED'
                            OR (s.status = 'CANCELLED' AND EXISTS (
                                SELECT 1 FROM sales_invoice_cancellations sic WHERE sic.sale_id = s.id
                            )))) AS total_sales_count,
                    (SELECT MAX(s.sale_date)
                     FROM sales s
                     WHERE s.customer_id = :customerId
                       AND s.status = 'COMPLETED') AS last_sale_date,
                    (SELECT MAX(CAST(sp.payment_date AS DATE))
                     FROM sales_payments sp
                     JOIN sales s ON s.id = sp.sale_id
                     WHERE s.customer_id = :customerId) AS last_payment_date
                """;
        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("customerId", customerId),
                (rs, rowNum) -> new CustomerActivityStats(
                        rs.getLong("total_sales_count"),
                        rs.getObject("last_sale_date", LocalDate.class),
                        rs.getObject("last_payment_date", LocalDate.class)
                )
        );
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s\\-()]", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
