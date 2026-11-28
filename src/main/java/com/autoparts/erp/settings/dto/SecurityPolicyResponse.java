package com.autoparts.erp.settings.dto;

public record SecurityPolicyResponse(
        int minPasswordLength,
        int maxPasswordLength,
        int maxFailedLoginAttempts,
        long lockDurationSeconds,
        long jwtExpirationSeconds,
        long refreshTokenExpirationSeconds,
        boolean readOnly,
        String managementNote
) {}
