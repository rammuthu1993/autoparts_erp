
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordResetRequest(

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128)
        String newPassword
) {
}
