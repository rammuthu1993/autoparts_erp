
package com.autoparts.erp.auth.model;

import java.util.List;

public record UserAccount(
        Long id,
        String username,
        String passwordHash,
        boolean enabled,
        List<String> roles
) {
}
