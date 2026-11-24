package com.autoparts.erp.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.auth.model.UserSecurityToken;

public interface UserSecurityTokenRepository {

    Long createToken(
            UUID uuid,
            Long userId,
            String tokenHash,
            String purpose,
            Instant expiresAt,
            int maxAttempts,
            Instant resendAvailableAt
    );

    Optional<UserSecurityToken> findActiveToken(String tokenHash, String purpose);

    Optional<UserSecurityToken> findLatestActiveToken(Long userId, String purpose);

    int incrementAttempts(Long id);

    boolean consumeToken(Long id);

    int invalidateAllForUserAndPurpose(Long userId, String purpose);

    int deleteExpiredTokens();
}
