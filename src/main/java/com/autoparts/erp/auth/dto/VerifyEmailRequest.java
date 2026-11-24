package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(

        @NotBlank(message = "Verification code is required")
        String token
) {
}
