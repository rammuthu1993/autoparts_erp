package com.autoparts.erp.auth.dto;

import java.util.UUID;
import com.autoparts.erp.auth.model.Permission;

public record PermissionResponse(
        UUID uuid,
        String code,
        String name,
        String description,
        String module,
        String action,
        boolean enabled
) {
    public static PermissionResponse from(Permission p) {
        if (p == null) {
            return null;
        }
        return new PermissionResponse(
                p.uuid(),
                p.code(),
                p.name(),
                p.description(),
                p.module(),
                p.action(),
                p.enabled()
        );
    }
}
