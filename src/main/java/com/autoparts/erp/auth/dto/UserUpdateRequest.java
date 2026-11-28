package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.Email;
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
        String username,

        @Email(message = "Email format is invalid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        Boolean clearEmail
) {
    public UserUpdateRequest {
        if (username != null) {
            username = username.trim();
        }
        if (email != null) {
            email = email.trim();
        }
    }

    public UserUpdateRequest(String username) {
        this(username, null, null);
    }

    public UserUpdateRequest(String username, String email) {
        this(username, email, null);
    }
}
