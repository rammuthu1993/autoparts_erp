package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AssignRolePermissionsRequest(
        @NotEmpty(message = "At least one permission must be assigned to the role")
        Set<String> permissionCodes
) {
}
