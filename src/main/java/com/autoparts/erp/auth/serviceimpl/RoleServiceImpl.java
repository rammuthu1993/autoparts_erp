
package com.autoparts.erp.auth.serviceimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.auth.dto.RoleResponse;
import com.autoparts.erp.auth.service.RoleService;

@Service
public class RoleServiceImpl implements RoleService {

    private final JdbcTemplate jdbcTemplate;

    public RoleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_SQL = """
        SELECT
            uuid,
            name,
            description,
            created_at
        FROM roles
        """;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {

        return jdbcTemplate.query(
                SELECT_SQL + " ORDER BY name",
                roleRowMapper()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse findByUuid(UUID uuid) {

        List<RoleResponse> roles = jdbcTemplate.query(
                SELECT_SQL + " WHERE uuid = ?",
                roleRowMapper(),
                uuid
        );

        return roles.stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Role not found"
                        )
                );
    }

    private RowMapper<RoleResponse> roleRowMapper() {

        return (rs, rowNum) -> mapRole(rs);
    }

    private RoleResponse mapRole(ResultSet rs)
            throws SQLException {

        return new RoleResponse(
                rs.getObject("uuid", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
