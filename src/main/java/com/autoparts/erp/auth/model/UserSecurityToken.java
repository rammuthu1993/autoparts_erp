package com.autoparts.erp.auth.model;

import java.time.Instant;
import java.util.UUID;

public record UserSecurityToken(
        Long id,
        UUID uuid,
        Long userId,
        String tokenHash,
        String purpose,
        Instant expiresAt,
        int attempts,
        int maxAttempts,
        Instant resendAvailableAt,
        Instant consumedAt,
        Instant createdAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isMaxAttemptsExceeded() {
        return attempts >= maxAttempts;
    }

    public boolean isResendCooldownActive() {
        return resendAvailableAt != null && Instant.now().isBefore(resendAvailableAt);
    }
}
