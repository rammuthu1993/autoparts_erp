
package com.autoparts.erp.auth.model;

import java.util.List;

public record UserAccount(
        Long id,
        String username,
        String email,
        boolean emailVerified,
        String passwordHash,
        boolean enabled,
        boolean accountNonLocked,
        List<String> roles
) {
    public UserAccount(
            Long id,
            String username,
            String passwordHash,
            boolean enabled,
            boolean accountNonLocked,
            List<String> roles
    ) {
        this(id, username, null, false, passwordHash, enabled, accountNonLocked, roles);
    }
}
