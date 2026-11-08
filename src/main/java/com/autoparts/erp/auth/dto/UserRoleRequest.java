
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UserRoleRequest(

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
