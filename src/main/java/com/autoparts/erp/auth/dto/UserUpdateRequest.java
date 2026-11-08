
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100)
        @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "Username contains invalid characters"
        )
        String username
) {
}
