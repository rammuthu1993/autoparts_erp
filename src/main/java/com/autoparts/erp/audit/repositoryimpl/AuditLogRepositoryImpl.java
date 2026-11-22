package com.autoparts.erp.audit.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.repository.AuditLogRepository;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditLogRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String INSERT_SQL = """
        INSERT INTO audit_logs (
            user_id,
            username,
            action,
            entity_type,
            entity_id,
            description,
            old_values,
            new_values
        )
        VALUES (
            :userId,
            :username,
            :action,
            :entityType,
            :entityId,
            :description,
            CAST(:oldValues AS JSONB),
            CAST(:newValues AS JSONB)
        )
        """;

    private static final String FIND_RECENT_SQL = """
        SELECT
            id,
            uuid,
            user_id,
            username,
            action,
            entity_type,
            entity_id,
            description,
            old_values::TEXT AS old_values,
            new_values::TEXT AS new_values,
            created_at
        FROM audit_logs
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """;

    @Override
    public void insert(
            Long userId,
            String username,
            String action,
            String entityType,
            String entityId,
            String description,
            String oldValues,
            String newValues
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("username", username)
                .addValue("action", action)
                .addValue("entityType", entityType)
                .addValue("entityId", entityId)
                .addValue("description", description)
                .addValue("oldValues", oldValues)
                .addValue("newValues", newValues);

        jdbcTemplate.update(INSERT_SQL, params);
    }

    private static final Map<String, String> AUDIT_SORT_ALLOWLIST = Map.of(
            "id", "id",
            "createdAt", "created_at",
            "action", "action",
            "entityType", "entity_type",
            "username", "username"
    );

    @Override
    public List<AuditLog> findRecent(int limit, int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbcTemplate.query(
                FIND_RECENT_SQL,
                params,
                auditLogRowMapper()
        );
    }

    @Override
    public List<AuditLog> findPaged(
            int offset,
            int limit,
            String search,
            String action,
            String entityType,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                id,
                uuid,
                user_id,
                username,
                action,
                entity_type,
                entity_id,
                description,
                old_values::TEXT AS old_values,
                new_values::TEXT AS new_values,
                created_at
            FROM audit_logs
            WHERE 1=1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyAuditFilters(sql, params, search, action, entityType);

        String sortColumn = AUDIT_SORT_ALLOWLIST.getOrDefault(sortBy, "created_at");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"id".equals(sortColumn)) {
            sql.append(", id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, auditLogRowMapper());
    }

    @Override
    public long countPaged(String search, String action, String entityType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applyAuditFilters(sql, params, search, action, entityType);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applyAuditFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            String search,
            String action,
            String entityType
    ) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND (description ILIKE :search OR username ILIKE :search OR entity_id ILIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = :action");
            params.addValue("action", action.trim().toUpperCase());
        }
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = :entityType");
            params.addValue("entityType", entityType.trim().toUpperCase());
        }
    }

    private RowMapper<AuditLog> auditLogRowMapper() {
        return (rs, rowNum) -> mapAuditLog(rs);
    }

    private AuditLog mapAuditLog(ResultSet rs)
            throws SQLException {

        return new AuditLog(
                rs.getLong("id"),
                rs.getString("uuid"),
                rs.getObject("user_id", Long.class),
                rs.getString("username"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("description"),
                rs.getString("old_values"),
                rs.getString("new_values"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
