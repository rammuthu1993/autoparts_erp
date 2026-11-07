
package com.autoparts.erp.auth.repository;

import java.util.Optional;

import com.autoparts.erp.auth.model.UserAccount;

public interface UserRepository {

    Optional<UserAccount> findByUsername(String username);

    int recordFailedLoginAttempt(
            String username,
            int maxFailedAttempts,
            long lockDurationSeconds
    );

    int resetFailedLoginAttempts(String username);
}
