package com.autoparts.erp.auth.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.UserManagementRepository;

@Repository
public class UserManagementRepositoryImpl
        implements UserManagementRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserManagementRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String USER_SELECT = """
        SELECT
            u.id,
            u.uuid,
            u.username,
            u.enabled,
            u.created_at,
            u.updated_at,
            r.name AS role_name
        FROM users u
        LEFT JOIN user_roles ur ON ur.user_id = u.id
        LEFT JOIN roles r ON r.id = ur.role_id
        """;

    @Override
    public List<ManagedUser> findAll() {
        return findUsers(USER_SELECT + """
            ORDER BY LOWER(u.username), r.name
            """, null);
    }

    @Override
    public Optional<ManagedUser> findByUuid(UUID uuid) {

        List<ManagedUser> users = findUsers(
                USER_SELECT + """
                    WHERE u.uuid = :uuid
                    ORDER BY r.name
                    """,
                uuid
        );

        return users.stream().findFirst();
    }

    private List<ManagedUser> findUsers(
            String sql,
            UUID uuid
    ) {
        MapSqlParameterSource params = uuid == null
                ? new MapSqlParameterSource()
                : new MapSqlParameterSource("uuid", uuid);

        List<Object[]> rows = jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> mapRow(rs)
        );

        Map<Long, UserBuilder> grouped = new LinkedHashMap<>();

        for (Object[] row : rows) {

            Long id = (Long) row[0];

            UserBuilder builder = grouped.computeIfAbsent(
                    id,
                    key -> new UserBuilder(
                            id,
                            (UUID) row[1],
                            (String) row[2],
                            (Boolean) row[3],
                            (java.time.Instant) row[4],
                            (java.time.Instant) row[5]
                    )
            );

            String roleName = (String) row[6];

            if (roleName != null) {
                builder.roles.add(roleName);
            }
        }

        return grouped.values()
                .stream()
                .map(UserBuilder::build)
                .toList();
    }

    private Object[] mapRow(ResultSet rs) throws SQLException {

        return new Object[] {
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getString("username"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("role_name")
        };
    }

    private static class UserBuilder {

        private final Long id;
        private final UUID uuid;
        private final String username;
        private final boolean enabled;
        private final java.time.Instant createdAt;
        private final java.time.Instant updatedAt;
        private final Set<String> roles = new LinkedHashSet<>();

        private UserBuilder(
                Long id,
                UUID uuid,
                String username,
                boolean enabled,
                java.time.Instant createdAt,
                java.time.Instant updatedAt
        ) {
            this.id = id;
            this.uuid = uuid;
            this.username = username;
            this.enabled = enabled;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private ManagedUser build() {
            return new ManagedUser(
                    id,
                    uuid,
                    username,
                    enabled,
                    Set.copyOf(roles),
                    createdAt,
                    updatedAt
            );
        }
    }

    @Override
    public boolean existsByUsername(
            String username,
            UUID excludeUuid
    ) {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM users
                WHERE LOWER(username) = LOWER(:username)
                  AND (CAST(:excludeUuid AS UUID) IS NULL OR uuid <> :excludeUuid)
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("excludeUuid", excludeUuid);

        Boolean exists = jdbcTemplate.queryForObject(
                sql,
                params,
                Boolean.class
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Long create(
            String username,
            String passwordHash,
            UUID uuid
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("username", username)
                .addValue("passwordHash", passwordHash);

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (
                    uuid,
                    username,
                    password_hash,
                    enabled
                )
                VALUES (:uuid, :username, :passwordHash, TRUE)
                RETURNING id
                """,
                params,
                Long.class
        );
    }

    @Override
    public void updateUsername(UUID uuid, String username) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("uuid", uuid);

        jdbcTemplate.update(
                """
                UPDATE users
                SET username = :username,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                """,
                params
        );
    }

    @Override
    public void updatePassword(UUID uuid, String passwordHash) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("passwordHash", passwordHash)
                .addValue("uuid", uuid);

        jdbcTemplate.update(
                """
                UPDATE users
                SET password_hash = :passwordHash,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                """,
                params
        );
    }

    @Override
    public void updateEnabled(UUID uuid, boolean enabled) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("enabled", enabled)
                .addValue("uuid", uuid);

        jdbcTemplate.update(
                """
                UPDATE users
                SET enabled = :enabled,
                    updated_at = CURRENT_TIMESTAMP
                WHERE uuid = :uuid
                """,
                params
        );
    }

    @Override
    public void replaceRoles(Long userId, Set<String> roleNames) {

        Map<String, Long> roleIds = new LinkedHashMap<>();

        for (String roleName : roleNames) {

            try {
                Long roleId = jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM roles
                        WHERE name = :roleName
                        """,
                        new MapSqlParameterSource("roleName", roleName),
                        Long.class
                );

                roleIds.put(roleName, roleId);

            } catch (EmptyResultDataAccessException ex) {
                throw new IllegalArgumentException(
                        "Role does not exist: " + roleName
                );
            }
        }

        // Replace role assignments only after validating all roles.
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId)
        );

        for (Long roleId : roleIds.values()) {

            MapSqlParameterSource insertParams = new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("roleId", roleId);

            jdbcTemplate.update(
                    """
                    INSERT INTO user_roles (user_id, role_id)
                    VALUES (:userId, :roleId)
                    """,
                    insertParams
            );
        }
    }

    @Override
    public List<Long> lockActiveAdminUsers() {

        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT u.id
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.enabled = TRUE
                  AND r.name = 'ADMIN'
                ORDER BY u.id
                FOR UPDATE OF u
                """,
                new MapSqlParameterSource(),
                Long.class
        );
    }

    @Override
    public long countActiveAdmins() {

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT u.id)
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.enabled = TRUE
                  AND r.name = 'ADMIN'
                """,
                new MapSqlParameterSource(),
                Long.class
        );

        return count == null ? 0 : count;
    }

    @Override
    public boolean isAdmin(Long userId) {

        Boolean result = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM user_roles ur
                    JOIN roles r ON r.id = ur.role_id
                    WHERE ur.user_id = :userId
                      AND r.name = 'ADMIN'
                )
                """,
                new MapSqlParameterSource("userId", userId),
                Boolean.class
        );

        return Boolean.TRUE.equals(result);
    }
}
