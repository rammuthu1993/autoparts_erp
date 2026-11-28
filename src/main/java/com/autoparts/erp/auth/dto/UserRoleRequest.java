package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserRoleRequest(

        @NotEmpty(message = "At least one role is required")
        Set<
            @NotBlank(message = "Role name must not be blank")
            @Size(max = 50, message = "Role name must not exceed 50 characters")
            String
        > roleNames
) {
}
