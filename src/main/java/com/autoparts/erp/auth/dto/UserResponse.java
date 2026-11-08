
package com.autoparts.erp.auth.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID uuid,
        String username,
        boolean enabled,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
