package com.autoparts.erp.auth.serviceimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
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

    private static final Set<String> SYSTEM_ROLES = Set.of("ADMIN", "MANAGER", "CASHIER");

    private final JdbcTemplate jdbcTemplate;

    public RoleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_SQL = """
        SELECT
            r.uuid,
            r.name,
            r.description,
            r.created_at,
            COALESCE(uc.user_count, 0) AS user_count,
            COALESCE(pc.permission_count, 0) AS permission_count
        FROM roles r
        LEFT JOIN (
            SELECT role_id, COUNT(*) AS user_count
            FROM user_roles
            GROUP BY role_id
        ) uc ON uc.role_id = r.id
        LEFT JOIN (
            SELECT rp.role_id, COUNT(*) AS permission_count
            FROM role_permissions rp
            JOIN permissions p ON p.id = rp.permission_id
            WHERE p.enabled = TRUE
            GROUP BY rp.role_id
        ) pc ON pc.role_id = r.id
        """;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return jdbcTemplate.query(
                SELECT_SQL + " ORDER BY r.name LIMIT 50",
                roleRowMapper()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse findByUuid(UUID uuid) {
        List<RoleResponse> roles = jdbcTemplate.query(
                SELECT_SQL + " WHERE r.uuid = ?",
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

    private RoleResponse mapRole(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        return new RoleResponse(
                rs.getObject("uuid", UUID.class),
                name,
                rs.getString("description"),
                rs.getLong("user_count"),
                rs.getLong("permission_count"),
                name != null && SYSTEM_ROLES.contains(name.toUpperCase()),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
