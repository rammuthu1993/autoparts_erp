
package com.autoparts.erp.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.auth.model.RefreshToken;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHashForUpdate(
            String tokenHash
    );

    void insert(
            Long userId,
            String tokenHash,
            UUID tokenFamily,
            Instant expiresAt
    );

    int revokeAndReplace(
            Long id,
            Instant revokedAt,
            String replacementTokenHash
    );

    int revokeTokenFamily(
            UUID tokenFamily,
            Instant revokedAt
    );

    int revokeTokenFamilyByTokenHash(
            String tokenHash,
            Instant revokedAt
    );
    
    void revokeAllActiveByUserId(Long userId);
}
