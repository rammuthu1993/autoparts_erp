package com.autoparts.erp.auth.repositoryimpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.auth.model.RefreshToken;
import com.autoparts.erp.auth.repository.RefreshTokenRepository;

@Repository
public class RefreshTokenRepositoryImpl
        implements RefreshTokenRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RefreshTokenRepositoryImpl(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String FIND_BY_HASH_FOR_UPDATE_SQL = """
        SELECT
            rt.id,
            rt.user_id,
            u.username,
            rt.token_hash,
            rt.token_family,
            rt.expires_at,
            rt.revoked_at
        FROM refresh_tokens rt
        JOIN users u ON u.id = rt.user_id
        WHERE rt.token_hash = :tokenHash
        FOR UPDATE OF rt
        """;

    private static final String INSERT_SQL = """
        INSERT INTO refresh_tokens (
            user_id,
            token_hash,
            token_family,
            expires_at
        )
        VALUES (:userId, :tokenHash, :tokenFamily, :expiresAt)
        """;

    private static final String REVOKE_AND_REPLACE_SQL = """
        UPDATE refresh_tokens
        SET
            revoked_at = :revokedAt,
            replaced_by_token_hash = :replacementTokenHash
        WHERE id = :id
          AND revoked_at IS NULL
        """;

    private static final String REVOKE_FAMILY_SQL = """
        UPDATE refresh_tokens
        SET revoked_at = :revokedAt
        WHERE token_family = :tokenFamily
          AND revoked_at IS NULL
        """;

    private static final String REVOKE_FAMILY_BY_HASH_SQL = """
        UPDATE refresh_tokens
        SET revoked_at = :revokedAt
        WHERE token_family = (
            SELECT token_family
            FROM refresh_tokens
            WHERE token_hash = :tokenHash
        )
        AND revoked_at IS NULL
        """;

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(
            String tokenHash
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource("tokenHash", tokenHash);

        List<RefreshToken> results = jdbcTemplate.query(
                FIND_BY_HASH_FOR_UPDATE_SQL,
                params,
                (rs, rowNum) -> mapRefreshToken(rs)
        );

        return results.stream().findFirst();
    }

    @Override
    public void insert(
            Long userId,
            String tokenHash,
            UUID tokenFamily,
            Instant expiresAt
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("tokenFamily", tokenFamily)
                .addValue("expiresAt", java.sql.Timestamp.from(expiresAt));

        jdbcTemplate.update(INSERT_SQL, params);
    }

    @Override
    public int revokeAndReplace(
            Long id,
            Instant revokedAt,
            String replacementTokenHash
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("revokedAt", java.sql.Timestamp.from(revokedAt))
                .addValue("replacementTokenHash", replacementTokenHash)
                .addValue("id", id);

        return jdbcTemplate.update(REVOKE_AND_REPLACE_SQL, params);
    }

    @Override
    public int revokeTokenFamily(
            UUID tokenFamily,
            Instant revokedAt
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("revokedAt", java.sql.Timestamp.from(revokedAt))
                .addValue("tokenFamily", tokenFamily);

        return jdbcTemplate.update(REVOKE_FAMILY_SQL, params);
    }

    @Override
    public int revokeTokenFamilyByTokenHash(
            String tokenHash,
            Instant revokedAt
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("revokedAt", java.sql.Timestamp.from(revokedAt))
                .addValue("tokenHash", tokenHash);

        return jdbcTemplate.update(REVOKE_FAMILY_BY_HASH_SQL, params);
    }

    private RefreshToken mapRefreshToken(
            ResultSet rs
    ) throws SQLException {

        return new RefreshToken(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("token_hash"),
                rs.getObject("token_family", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") != null
                        ? rs.getTimestamp("revoked_at").toInstant()
                        : null
        );
    }

    @Override
    public void revokeAllActiveByUserId(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);

        jdbcTemplate.update(
                """
                UPDATE refresh_tokens
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId
                  AND revoked_at IS NULL
                """,
                params
        );
    }
}
