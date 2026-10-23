
package com.autoparts.erp.auth.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repository.UserRepository;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String FIND_BY_USERNAME_SQL = """
        SELECT
            u.id,
            u.username,
            u.password_hash,
            u.enabled,
            r.name AS role_name
        FROM users u
        LEFT JOIN user_roles ur ON ur.user_id = u.id
        LEFT JOIN roles r ON r.id = ur.role_id
        WHERE LOWER(u.username) = LOWER(?)
        ORDER BY r.name
        """;

    @Override
    public Optional<UserAccount> findByUsername(String username) {

        List<UserAccount> results = jdbcTemplate.query(
                FIND_BY_USERNAME_SQL,
                (ResultSetExtractor<List<UserAccount>>) rs ->
                        extractUser(rs, username),
                username
        );

        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }

    private List<UserAccount> extractUser(
            ResultSet rs,
            String username) throws SQLException {

        if (!rs.next()) {
            return List.of();
        }

        Long id = rs.getLong("id");
        String dbUsername = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        boolean enabled = rs.getBoolean("enabled");

        List<String> roles = new ArrayList<>();

        do {
            String roleName = rs.getString("role_name");

            if (roleName != null && !roleName.isBlank()) {
                roles.add("ROLE_" + roleName);
            }
        } while (rs.next());

        UserAccount user = new UserAccount(
                id,
                dbUsername,
                passwordHash,
                enabled,
                List.copyOf(roles)
        );

        return List.of(user);
    }
}
