package com.autoparts.erp.repository;

import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.model.RefreshToken;
import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repositoryimpl.RefreshTokenRepositoryImpl;
import com.autoparts.erp.auth.repositoryimpl.UserManagementRepositoryImpl;
import com.autoparts.erp.auth.repositoryimpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Batch E — Security and Authentication PostgreSQL Integration Tests")
class BatchESecurityAuthenticationIntegrationTest extends BaseRepositoryIntegrationTest {

    private UserRepositoryImpl userRepository;
    private UserManagementRepositoryImpl userManagementRepository;
    private RefreshTokenRepositoryImpl refreshTokenRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryImpl(getJdbcTemplate());
        userManagementRepository = new UserManagementRepositoryImpl(getJdbcTemplate());
        refreshTokenRepository = new RefreshTokenRepositoryImpl(getJdbcTemplate());
    }

    @Test
    @DisplayName("User and UserManagement: user creation, role assignment, lock and failed attempts")
    void testUserManagementAndAccountStatus() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID userUuid = randomUuid();
            String username = "sec_user_" + UUID.randomUUID().toString().substring(0, 6);

            Long userId = userManagementRepository.create(username, "hash_bcrypt_xyz", userUuid);
            assertNotNull(userId);

            // Assign roles
            userManagementRepository.replaceRoles(userId, Set.of("ADMIN", "CASHIER"));

            ManagedUser managed = userManagementRepository.findByUuid(userUuid).orElseThrow();
            assertEquals(username, managed.username());
            assertTrue(managed.enabled());
            assertEquals(2, managed.roles().size());
            assertTrue(managed.roles().contains("ADMIN"));
            assertTrue(managed.roles().contains("CASHIER"));

            // Check UserRepository view
            UserAccount account = userRepository.findByUsername(username).orElseThrow();
            assertEquals(username, account.username());
            assertTrue(account.accountNonLocked());

            // Record failed login attempts
            userRepository.recordFailedLoginAttempt(username, 5, 900);
            userRepository.recordFailedLoginAttempt(username, 5, 900);
            userRepository.recordFailedLoginAttempt(username, 5, 900);
            userRepository.recordFailedLoginAttempt(username, 5, 900);
            userRepository.recordFailedLoginAttempt(username, 5, 900); // 5th attempt locks account

            UserAccount lockedAccount = userRepository.findByUsername(username).orElseThrow();
            assertFalse(lockedAccount.accountNonLocked(), "Account must be locked after 5 failed attempts");

            // Reset failed login attempts
            userRepository.resetFailedLoginAttempts(username);
            UserAccount unlockedAccount = userRepository.findByUsername(username).orElseThrow();
            assertTrue(unlockedAccount.accountNonLocked(), "Account must be unlocked after reset");

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("RefreshTokenRepository: token insertion, family revocation and user-wide revocation")
    void testRefreshTokenLifecycle() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID userUuid = randomUuid();
            String username = "token_user_" + UUID.randomUUID().toString().substring(0, 6);
            Long userId = userManagementRepository.create(username, "hash123", userUuid);

            UUID familyUuid = randomUuid();
            String tokenHash1 = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
            Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

            refreshTokenRepository.insert(userId, tokenHash1, familyUuid, expiresAt);

            Optional<RefreshToken> token1Opt = refreshTokenRepository.findByTokenHashForUpdate(tokenHash1);
            assertTrue(token1Opt.isPresent());
            RefreshToken token1 = token1Opt.get();
            assertEquals(userId, token1.userId());
            assertEquals(username, token1.username());
            assertNull(token1.revokedAt());

            // Revoke and replace (rotation)
            String tokenHash2 = "f0e9d8c7b6a594837261504f3e2d1c0baf0e9d8c7b6a594837261504f3e2d1c0";
            int updated = refreshTokenRepository.revokeAndReplace(token1.id(), Instant.now(), tokenHash2);
            assertEquals(1, updated);

            // Re-fetch token1 to verify revoked
            RefreshToken revokedToken1 = refreshTokenRepository.findByTokenHashForUpdate(tokenHash1).orElseThrow();
            assertNotNull(revokedToken1.revokedAt());

            // Insert new rotated token in the same family
            refreshTokenRepository.insert(userId, tokenHash2, familyUuid, expiresAt);

            // Revoke entire token family (compromise detection)
            int familyRevoked = refreshTokenRepository.revokeTokenFamily(familyUuid, Instant.now());
            assertTrue(familyRevoked >= 1);

            // Revoke all active by user
            String tokenHash3 = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
            refreshTokenRepository.insert(userId, tokenHash3, randomUuid(), expiresAt);
            refreshTokenRepository.revokeAllActiveByUserId(userId);

            status.setRollbackOnly();
        });
    }
}
