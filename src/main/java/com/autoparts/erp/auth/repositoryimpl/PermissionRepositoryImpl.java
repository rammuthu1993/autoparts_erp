package com.autoparts.erp.auth.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.auth.model.Permission;
import com.autoparts.erp.auth.repository.PermissionRepository;

@Repository
public class PermissionRepositoryImpl implements PermissionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PermissionRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Permission> PERMISSION_ROW_MAPPER = (rs, rowNum) -> mapPermission(rs);

    private static final String SELECT_BASE = """
        SELECT
            id,
            uuid,
            code,
            name,
            description,
            module,
            action,
            enabled,
            created_at,
            updated_at
        FROM permissions
        """;

    @Override
    public List<Permission> findAll(String module, String search) {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (module != null && !module.isBlank()) {
            sql.append(" AND module = :module ");
            params.addValue("module", module.trim().toUpperCase());
        }

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(code) LIKE :search OR LOWER(name) LIKE :search OR LOWER(description) LIKE :search) ");
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }

        sql.append(" ORDER BY module, code ");

        return jdbcTemplate.query(sql.toString(), params, PERMISSION_ROW_MAPPER);
    }

    @Override
    public Optional<Permission> findByUuid(UUID uuid) {
        MapSqlParameterSource params = new MapSqlParameterSource("uuid", uuid);
        String sql = SELECT_BASE + " WHERE uuid = :uuid";

        List<Permission> list = jdbcTemplate.query(sql, params, PERMISSION_ROW_MAPPER);
        return list.stream().findFirst();
    }

    @Override
    public Optional<Permission> findByCode(String code) {
        MapSqlParameterSource params = new MapSqlParameterSource("code", code.trim().toUpperCase());
        String sql = SELECT_BASE + " WHERE code = :code";

        List<Permission> list = jdbcTemplate.query(sql, params, PERMISSION_ROW_MAPPER);
        return list.stream().findFirst();
    }

    @Override
    public List<Permission> findByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("codes", codes);
        String sql = SELECT_BASE + " WHERE code IN (:codes) AND enabled = TRUE ORDER BY module, code";

        return jdbcTemplate.query(sql, params, PERMISSION_ROW_MAPPER);
    }

    @Override
    public List<Permission> findByRoleId(Long roleId) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleId", roleId);
        String sql = """
            SELECT
                p.id,
                p.uuid,
                p.code,
                p.name,
                p.description,
                p.module,
                p.action,
                p.enabled,
                p.created_at,
                p.updated_at
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            WHERE rp.role_id = :roleId
              AND p.enabled = TRUE
            ORDER BY p.module, p.code
            """;

        return jdbcTemplate.query(sql, params, PERMISSION_ROW_MAPPER);
    }

    @Override
    public List<Permission> findByRoleUuid(UUID roleUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleUuid", roleUuid);
        String sql = """
            SELECT
                p.id,
                p.uuid,
                p.code,
                p.name,
                p.description,
                p.module,
                p.action,
                p.enabled,
                p.created_at,
                p.updated_at
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            INNER JOIN roles r ON rp.role_id = r.id
            WHERE r.uuid = :roleUuid
              AND p.enabled = TRUE
            ORDER BY p.module, p.code
            """;

        return jdbcTemplate.query(sql, params, PERMISSION_ROW_MAPPER);
    }

    @Override
    public Set<String> findPermissionCodesByRoleId(Long roleId) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleId", roleId);
        String sql = """
            SELECT p.code
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            WHERE rp.role_id = :roleId
              AND p.enabled = TRUE
            """;

        List<String> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("code"));
        return new HashSet<>(list);
    }

    @Override
    public Set<String> findPermissionCodesByRoleNames(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Collections.emptySet();
        }

        // Clean any "ROLE_" prefixes so we match roles.name directly
        Set<String> cleanRoleNames = new HashSet<>();
        for (String r : roleNames) {
            if (r != null && !r.isBlank()) {
                String clean = r.startsWith("ROLE_") ? r.substring(5) : r;
                cleanRoleNames.add(clean.trim().toUpperCase());
            }
        }

        if (cleanRoleNames.isEmpty()) {
            return Collections.emptySet();
        }

        MapSqlParameterSource params = new MapSqlParameterSource("roleNames", cleanRoleNames);
        String sql = """
            SELECT DISTINCT p.code
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            INNER JOIN roles r ON rp.role_id = r.id
            WHERE r.name IN (:roleNames)
              AND p.enabled = TRUE
            """;

        List<String> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("code"));
        return new HashSet<>(list);
    }

    @Override
    public Set<String> findPermissionCodesByUserId(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        String sql = """
            SELECT DISTINCT p.code
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            INNER JOIN user_roles ur ON rp.role_id = ur.role_id
            INNER JOIN users u ON ur.user_id = u.id
            WHERE ur.user_id = :userId
              AND u.enabled = TRUE
              AND p.enabled = TRUE
            """;

        List<String> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("code"));
        return new HashSet<>(list);
    }

    @Override
    public Set<String> findRoleNamesByUserId(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        String sql = """
            SELECT r.name
            FROM roles r
            INNER JOIN user_roles ur ON r.id = ur.role_id
            INNER JOIN users u ON ur.user_id = u.id
            WHERE ur.user_id = :userId
              AND u.enabled = TRUE
            """;

        List<String> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("name"));
        return new HashSet<>(list);
    }

    @Override
    public Optional<Long> findRoleIdByUuid(UUID roleUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleUuid", roleUuid);
        String sql = "SELECT id FROM roles WHERE uuid = :roleUuid";

        List<Long> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getLong("id"));
        return list.stream().findFirst();
    }

    @Override
    public Optional<String> findRoleNameByUuid(UUID roleUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleUuid", roleUuid);
        String sql = "SELECT name FROM roles WHERE uuid = :roleUuid";

        List<String> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("name"));
        return list.stream().findFirst();
    }

    @Override
    public void replaceRolePermissions(Long roleId, Collection<Long> permissionIds, Long assignedByUserId) {
        MapSqlParameterSource deleteParams = new MapSqlParameterSource("roleId", roleId);
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = :roleId", deleteParams);

        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batchParams = permissionIds.stream()
                .map(permId -> new MapSqlParameterSource()
                        .addValue("roleId", roleId)
                        .addValue("permissionId", permId)
                        .addValue("assignedBy", assignedByUserId))
                .toArray(MapSqlParameterSource[]::new);

        String insertSql = """
            INSERT INTO role_permissions (role_id, permission_id, assigned_by)
            VALUES (:roleId, :permissionId, :assignedBy)
            ON CONFLICT (role_id, permission_id) DO NOTHING
            """;

        jdbcTemplate.batchUpdate(insertSql, batchParams);
    }

    private static Permission mapPermission(ResultSet rs) throws SQLException {
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");

        return new Permission(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("module"),
                rs.getString("action"),
                rs.getBoolean("enabled"),
                createdAtTs != null ? createdAtTs.toInstant() : Instant.now(),
                updatedAtTs != null ? updatedAtTs.toInstant() : Instant.now()
        );
    }
}
