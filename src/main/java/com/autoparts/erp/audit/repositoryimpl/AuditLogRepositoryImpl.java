
package com.autoparts.erp.audit.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.repository.AuditLogRepository;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepositoryImpl(JdbcTemplate jdbcTemplate) {
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
            ?, ?, ?, ?, ?, ?,
            CAST(? AS JSONB),
            CAST(? AS JSONB)
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
        LIMIT ? OFFSET ?
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

        jdbcTemplate.update(
                INSERT_SQL,
                userId,
                username,
                action,
                entityType,
                entityId,
                description,
                oldValues,
                newValues
        );
    }

    @Override
    public List<AuditLog> findRecent(int limit, int offset) {

        return jdbcTemplate.query(
                FIND_RECENT_SQL,
                auditLogRowMapper(),
                limit,
                offset
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
