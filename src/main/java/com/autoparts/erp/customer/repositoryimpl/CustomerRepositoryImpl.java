package com.autoparts.erp.customer.repositoryimpl;

import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repository.CustomerRepository;

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
public class CustomerRepositoryImpl implements CustomerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerRepositoryImpl(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Customer> ROW_MAPPER = (rs, rowNum) ->
            new Customer(
                    rs.getLong("id"),
                    rs.getObject("uuid", UUID.class),
                    rs.getString("customer_code"),
                    rs.getString("customer_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("address"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getString("pincode"),
                    rs.getString("gstin"),
                    rs.getBoolean("active"),
                    rs.getObject("created_at",
                            java.time.OffsetDateTime.class),
                    rs.getObject("updated_at",
                            java.time.OffsetDateTime.class)
            );

    private static final String SELECT_COLUMNS = """
            SELECT id, uuid, customer_code, customer_name,
                   phone, email, address, city, state,
                   pincode, gstin, active, created_at, updated_at
            FROM customers
            """;

    @Override
    public Customer create(Customer customer) {

        String sql = """
                INSERT INTO customers (
                    uuid,
                    customer_code,
                    customer_name,
                    phone,
                    email,
                    address,
                    city,
                    state,
                    pincode,
                    gstin
                )
                VALUES (
                    :uuid,
                    'CUS' || LPAD(
                        nextval('customer_code_seq')::TEXT,
                        6,
                        '0'
                    ),
                    :customerName,
                    :phone,
                    :email,
                    :address,
                    :city,
                    :state,
                    :pincode,
                    :gstin
                )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", customer.uuid())
                .addValue("customerName", customer.customerName())
                .addValue("phone", customer.phone())
                .addValue("email", customer.email())
                .addValue("address", customer.address())
                .addValue("city", customer.city())
                .addValue("state", customer.state())
                .addValue("pincode", customer.pincode())
                .addValue("gstin", customer.gstin());

        return jdbcTemplate.queryForObject(sql, params, ROW_MAPPER);
    }

    private static final Map<String, String> CUSTOMER_SORT_ALLOWLIST = Map.of(
            "id", "id",
            "customerName", "customer_name",
            "customerCode", "customer_code",
            "phone", "phone",
            "city", "city",
            "createdAt", "created_at"
    );

    @Override
    public List<Customer> findAll() {

        String sql = SELECT_COLUMNS + """
                WHERE active = TRUE
                ORDER BY customer_name, id ASC
                LIMIT 501
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource(),
                ROW_MAPPER
        );
    }

    @Override
    public List<Customer> findPaged(
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

        applyCustomerFilters(sql, params, search, city, state);

        String sortColumn = CUSTOMER_SORT_ALLOWLIST.getOrDefault(sortBy, "customer_name");
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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM customers WHERE active = TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyCustomerFilters(sql, params, search, city, state);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyCustomerFilters(
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
                     LOWER(customer_name) LIKE :searchPattern
                     OR LOWER(customer_code) LIKE :searchPattern
                     OR phone LIKE :searchPattern
                 )
            """);
            params.addValue("searchPattern", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    @Override
    public Optional<Customer> findByUuid(UUID uuid) {

        String sql = SELECT_COLUMNS + """
                WHERE uuid = :uuid
                  AND active = TRUE
                """;

        List<Customer> customers = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("uuid", uuid),
                ROW_MAPPER
        );

        return customers.stream().findFirst();
    }

    @Override
    public Optional<Customer> findById(Long id) {

        String sql = SELECT_COLUMNS + """
                WHERE id = :id
                """;

        List<Customer> customers = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );

        return customers.stream().findFirst();
    }

    @Override
    public boolean update(Customer customer) {

        String sql = """
                UPDATE customers
                SET customer_name = :customerName,
                    phone = :phone,
                    email = :email,
                    address = :address,
                    city = :city,
                    state = :state,
                    pincode = :pincode,
                    gstin = :gstin,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND active = TRUE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", customer.id())
                .addValue("customerName", customer.customerName())
                .addValue("phone", customer.phone())
                .addValue("email", customer.email())
                .addValue("address", customer.address())
                .addValue("city", customer.city())
                .addValue("state", customer.state())
                .addValue("pincode", customer.pincode())
                .addValue("gstin", customer.gstin());

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

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource("id", id)
        ) > 0;
    }
}