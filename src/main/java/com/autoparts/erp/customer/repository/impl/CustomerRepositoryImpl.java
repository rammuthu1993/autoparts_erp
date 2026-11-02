package com.autoparts.erp.customer.repository.impl;

import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repository.CustomerRepository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Override
    public List<Customer> findAll() {

        String sql = SELECT_COLUMNS + """
                WHERE active = TRUE
                ORDER BY customer_name
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource(),
                ROW_MAPPER
        );
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