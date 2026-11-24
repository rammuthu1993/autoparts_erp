package com.autoparts.erp.auth.dto;

public record VerifyOtpResponse(
        String resetToken,
        long expiresInSeconds
) {
}
