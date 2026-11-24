package com.autoparts.erp.auth.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repository.UserRepository;

@Repository
public class UserRepositoryImpl implements UserRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public UserRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String FIND_BY_USERNAME_SQL = """
			SELECT
			    u.id,
			    u.username,
			    u.password_hash,
			    u.enabled,
			    CASE
			        WHEN u.locked_until IS NULL
			            OR u.locked_until <= CURRENT_TIMESTAMP
			        THEN TRUE
			        ELSE FALSE
			    END AS account_non_locked,
			    r.name AS role_name
			FROM users u
			LEFT JOIN user_roles ur ON ur.user_id = u.id
			LEFT JOIN roles r ON r.id = ur.role_id
			WHERE LOWER(u.username) = LOWER(:username)
			ORDER BY r.name
			""";

	private static final String RECORD_FAILED_LOGIN_SQL = """
			UPDATE users AS u
			SET
			    failed_login_attempts = CASE
			        WHEN u.locked_until IS NOT NULL
			             AND u.locked_until <= CURRENT_TIMESTAMP
			        THEN 1
			        ELSE u.failed_login_attempts + 1
			    END,

			    locked_until = CASE
			        WHEN (
			            CASE
			                WHEN u.locked_until IS NOT NULL
			                     AND u.locked_until <= CURRENT_TIMESTAMP
			                THEN 1
			                ELSE u.failed_login_attempts + 1
			            END
			        ) >= :maxFailedAttempts
			        THEN CURRENT_TIMESTAMP + (:lockDurationSeconds * INTERVAL '1 second')
			        ELSE NULL
			    END,

			    updated_at = CURRENT_TIMESTAMP

			WHERE LOWER(u.username) = LOWER(:username)
			""";

	private static final String RESET_FAILED_LOGIN_SQL = """
			UPDATE users
			SET
			    failed_login_attempts = 0,
			    locked_until = NULL,
			    updated_at = CURRENT_TIMESTAMP
			WHERE LOWER(username) = LOWER(:username)
			""";

	@Override
	public Optional<UserAccount> findByUsername(String username) {
		MapSqlParameterSource params = new MapSqlParameterSource("username", username);

		List<UserAccount> results = jdbcTemplate.query(FIND_BY_USERNAME_SQL, params,
				(ResultSetExtractor<List<UserAccount>>) rs -> extractUser(rs));
     
		if (results == null || results.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(results.get(0));
	}

	@Override
	public int recordFailedLoginAttempt(String username, int maxFailedAttempts, long lockDurationSeconds) {

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("maxFailedAttempts", maxFailedAttempts)
				.addValue("lockDurationSeconds", lockDurationSeconds).addValue("username", username);

		return jdbcTemplate.update(RECORD_FAILED_LOGIN_SQL, params);
	}

	@Override
	public int resetFailedLoginAttempts(String username) {
		MapSqlParameterSource params = new MapSqlParameterSource("username", username);

		return jdbcTemplate.update(RESET_FAILED_LOGIN_SQL, params);
	}

	private List<UserAccount> extractUser(ResultSet rs) throws SQLException {

		if (!rs.next()) {
			return List.of();
		}

		Long id = rs.getLong("id");

		String dbUsername = rs.getString("username");

		String passwordHash = rs.getString("password_hash");

		boolean enabled = rs.getBoolean("enabled");

		boolean accountNonLocked = rs.getBoolean("account_non_locked");

		List<String> roles = new ArrayList<>();

		do {
			String roleName = rs.getString("role_name");

			if (roleName != null && !roleName.isBlank()) {
				roles.add("ROLE_" + roleName);
			}

		} while (rs.next());

		UserAccount user = new UserAccount(id, dbUsername, passwordHash, enabled, accountNonLocked, List.copyOf(roles));

		return List.of(user);
	}
}
