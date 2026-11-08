package com.autoparts.erp.auth.service;

import java.util.Optional;

public interface RefreshTokenService {

    IssuedRefreshToken createRefreshToken(Long userId);

    Optional<RotatedTokens> refresh(String rawToken);

    void revokeRefreshToken(String rawToken);

    void revokeAllForUser(Long userId);

    record IssuedRefreshToken(
            String refreshToken,
            long expiresInSeconds
    ) {
    }

    record RotatedTokens(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            long refreshExpiresInSeconds,
            String username
    ) {
    }
}