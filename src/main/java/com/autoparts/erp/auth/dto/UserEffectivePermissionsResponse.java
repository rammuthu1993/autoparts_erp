package com.autoparts.erp.auth.dto;

import java.util.Set;
import java.util.UUID;

public record UserEffectivePermissionsResponse(
        UUID userUuid,
        String username,
        Set<String> roles,
        Set<String> permissions
) {
}
