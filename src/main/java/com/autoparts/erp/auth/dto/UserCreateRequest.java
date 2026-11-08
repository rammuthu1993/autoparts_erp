
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserCreateRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100)
        @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "Username contains invalid characters"
        )
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128)
        String password,

        @NotEmpty(message = "At least one role is required")
        Set<
            @Pattern(
                regexp = "ADMIN|MANAGER|CASHIER",
                message = "Unsupported role"
            )
            String
        > roleNames
) {
}
