
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.Email;
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

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

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
    public UserCreateRequest {
        if (username != null) {
            username = username.trim();
        }
        if (email != null) {
            email = email.trim();
        }
    }

    public UserCreateRequest(String username, String password, Set<String> roleNames) {
        this(username, username != null ? username.toLowerCase() + "@example.com" : null, password, roleNames);
    }
}
