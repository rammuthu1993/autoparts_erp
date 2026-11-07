
package com.autoparts.erp.auth.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        Long id,
        Long userId,
        String username,
        String tokenHash,
        UUID tokenFamily,
        Instant expiresAt,
        Instant revokedAt
) {
}
