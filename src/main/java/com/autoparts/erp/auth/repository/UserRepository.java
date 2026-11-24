
package com.autoparts.erp.auth.repository;

import java.util.Optional;

import com.autoparts.erp.auth.model.UserAccount;

public interface UserRepository {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(Long id);

    int updatePassword(Long userId, String passwordHash);

    int recordFailedLoginAttempt(
            String username,
            int maxFailedAttempts,
            long lockDurationSeconds
    );

    int resetFailedLoginAttempts(String username);
}
