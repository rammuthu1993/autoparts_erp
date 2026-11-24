package com.autoparts.erp.auth.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.auth.model.UserSecurityToken;
import com.autoparts.erp.auth.repository.UserSecurityTokenRepository;

@Repository
public class UserSecurityTokenRepositoryImpl implements UserSecurityTokenRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserSecurityTokenRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserSecurityToken> TOKEN_ROW_MAPPER = (rs, rowNum) -> mapToken(rs);

    private static final String SELECT_BASE = """
        SELECT
            id,
            uuid,
            user_id,
            token_hash,
            purpose,
            expires_at,
            attempts,
            max_attempts,
            resend_available_at,
            consumed_at,
            created_at
        FROM user_security_tokens
        """;

    @Override
    public Long createToken(
            UUID uuid,
            Long userId,
            String tokenHash,
            String purpose,
            Instant expiresAt,
            int maxAttempts,
            Instant resendAvailableAt
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("purpose", purpose)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("maxAttempts", maxAttempts)
                .addValue("resendAvailableAt", resendAvailableAt != null ? Timestamp.from(resendAvailableAt) : null);

        String sql = """
            INSERT INTO user_security_tokens (
                uuid,
                user_id,
                token_hash,
                purpose,
                expires_at,
                max_attempts,
                resend_available_at
            )
            VALUES (
                :uuid,
                :userId,
                :tokenHash,
                :purpose,
                :expiresAt,
                :maxAttempts,
                :resendAvailableAt
            )
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    @Override
    public Optional<UserSecurityToken> findActiveToken(String tokenHash, String purpose) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tokenHash", tokenHash)
                .addValue("purpose", purpose);

        String sql = SELECT_BASE + """
            WHERE token_hash = :tokenHash
              AND purpose = :purpose
              AND consumed_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
            ORDER BY created_at DESC
            LIMIT 1
            """;

        List<UserSecurityToken> list = jdbcTemplate.query(sql, params, TOKEN_ROW_MAPPER);
        return list.stream().findFirst();
    }

    @Override
    public Optional<UserSecurityToken> findLatestActiveToken(Long userId, String purpose) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("purpose", purpose);

        String sql = SELECT_BASE + """
            WHERE user_id = :userId
              AND purpose = :purpose
              AND consumed_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
            ORDER BY created_at DESC
            LIMIT 1
            """;

        List<UserSecurityToken> list = jdbcTemplate.query(sql, params, TOKEN_ROW_MAPPER);
        return list.stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementAttempts(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        String sql = """
            UPDATE user_security_tokens
            SET attempts = attempts + 1
            WHERE id = :id
            """;

        return jdbcTemplate.update(sql, params);
    }

    @Override
    public boolean consumeToken(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        String sql = """
            UPDATE user_security_tokens
            SET consumed_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND consumed_at IS NULL
            """;

        int rows = jdbcTemplate.update(sql, params);
        return rows > 0;
    }

    @Override
    public int invalidateAllForUserAndPurpose(Long userId, String purpose) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("purpose", purpose);

        String sql = """
            UPDATE user_security_tokens
            SET consumed_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND purpose = :purpose
              AND consumed_at IS NULL
            """;

        return jdbcTemplate.update(sql, params);
    }

    @Override
    public int deleteExpiredTokens() {
        String sql = """
            DELETE FROM user_security_tokens
            WHERE (expires_at < CURRENT_TIMESTAMP - INTERVAL '7 days')
               OR (consumed_at IS NOT NULL AND consumed_at < CURRENT_TIMESTAMP - INTERVAL '7 days')
            """;

        return jdbcTemplate.update(sql, new MapSqlParameterSource());
    }

    private static UserSecurityToken mapToken(ResultSet rs) throws SQLException {
        Timestamp resendTs = rs.getTimestamp("resend_available_at");
        Timestamp consumedTs = rs.getTimestamp("consumed_at");

        return new UserSecurityToken(
                rs.getLong("id"),
                rs.getObject("uuid", UUID.class),
                rs.getLong("user_id"),
                rs.getString("token_hash"),
                rs.getString("purpose"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                resendTs != null ? resendTs.toInstant() : null,
                consumedTs != null ? consumedTs.toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
