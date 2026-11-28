package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignRolePermissionsRequest(
        @NotNull(message = "Permission codes list must not be null")
        Set<String> permissionCodes
) {
}
