package com.autoparts.erp.auth.dto;

import java.util.List;
import java.util.UUID;

public record RolePermissionsResponse(
        UUID roleUuid,
        String roleName,
        String description,
        List<PermissionResponse> permissions
) {
}
