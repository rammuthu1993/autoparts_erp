
package com.autoparts.erp.masters.supplier.repository;

import com.autoparts.erp.masters.supplier.model.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SupplierRepositoryImpl implements SupplierRepository {

    private final JdbcTemplate jdbcTemplate;

    public SupplierRepositoryImpl(JdbcTemplate jdbcTemplate) {
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, uuid, supplier_name, contact_person, phone,
                      email, address, city, state, pincode, gstin,
                      payment_terms_days, active, created_at, updated_at
            """;

        return jdbcTemplate.queryForObject(
            sql,
            ROW_MAPPER,
            supplier.uuid(),
            supplier.supplierName(),
            supplier.contactPerson(),
            supplier.phone(),
            supplier.email(),
            supplier.address(),
            supplier.city(),
            supplier.state(),
            supplier.pincode(),
            supplier.gstin(),
            supplier.paymentTermsDays()
        );
    }

    @Override
    public List<Supplier> findAll() {
        return jdbcTemplate.query(
            SELECT_COLUMNS + " ORDER BY supplier_name",
            ROW_MAPPER
        );
    }

    @Override
    public Optional<Supplier> findByUuid(UUID uuid) {
        List<Supplier> results = jdbcTemplate.query(
            SELECT_COLUMNS + " WHERE uuid = ?",
            ROW_MAPPER,
            uuid
        );

        return results.stream().findFirst();
    }

    @Override
    public boolean update(UUID uuid, Supplier supplier) {
        String sql = """
            UPDATE suppliers
            SET supplier_name = ?,
                contact_person = ?,
                phone = ?,
                email = ?,
                address = ?,
                city = ?,
                state = ?,
                pincode = ?,
                gstin = ?,
                payment_terms_days = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
              AND active = TRUE
            """;

        return jdbcTemplate.update(
            sql,
            supplier.supplierName(),
            supplier.contactPerson(),
            supplier.phone(),
            supplier.email(),
            supplier.address(),
            supplier.city(),
            supplier.state(),
            supplier.pincode(),
            supplier.gstin(),
            supplier.paymentTermsDays(),
            uuid
        ) > 0;
    }

    @Override
    public boolean deactivate(UUID uuid) {
        String sql = """
            UPDATE suppliers
            SET active = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ?
              AND active = TRUE
            """;

        return jdbcTemplate.update(sql, uuid) > 0;
    }
    

	@Override
	public boolean existsBySupplierName(String supplierName) {
	    String sql = """
	        SELECT EXISTS (
	            SELECT 1
	            FROM suppliers
	            WHERE LOWER(TRIM(supplier_name)) = LOWER(TRIM(?))
	        )
	        """;
	
	    return Boolean.TRUE.equals(
	        jdbcTemplate.queryForObject(sql, Boolean.class, supplierName)
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
	                TRIM(?), '[[:space:]]+', '', 'g'
	            )
	        )
	        """;
	
	    return Boolean.TRUE.equals(
	        jdbcTemplate.queryForObject(sql, Boolean.class, phone)
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
	              AND LOWER(TRIM(email)) = LOWER(TRIM(?))
	        )
	        """;
	
	    return Boolean.TRUE.equals(
	        jdbcTemplate.queryForObject(sql, Boolean.class, email)
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
	              AND UPPER(TRIM(gstin)) = UPPER(TRIM(?))
	        )
	        """;
	
	    return Boolean.TRUE.equals(
	        jdbcTemplate.queryForObject(sql, Boolean.class, gstin)
	    );
	}
	

//	@Override
//	public boolean existsBySupplierNameExcludingUuid(
//	    String supplierName, UUID uuid
//	) {
//	    String sql = """
//	        SELECT EXISTS (
//	            SELECT 1
//	            FROM suppliers
//	            WHERE LOWER(TRIM(supplier_name)) = LOWER(TRIM(?))
//	              AND uuid <> ?
//	        )
//	        """;
//	
//	    return Boolean.TRUE.equals(
//	        jdbcTemplate.queryForObject(
//	            sql, Boolean.class, supplierName, uuid
//	        )
//	    );
//	}


}
