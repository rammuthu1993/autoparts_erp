package com.autoparts.erp.audit.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
