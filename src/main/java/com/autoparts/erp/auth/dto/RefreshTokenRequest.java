
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required.")
        @Size(max = 256, message = "Invalid refresh token.")
        String refreshToken

) {
}
