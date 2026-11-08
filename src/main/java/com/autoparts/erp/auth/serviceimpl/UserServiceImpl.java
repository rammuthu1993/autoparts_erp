
package com.autoparts.erp.auth.serviceimpl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.dto.UserCreateRequest;
import com.autoparts.erp.auth.dto.UserPasswordResetRequest;
import com.autoparts.erp.auth.dto.UserResponse;
import com.autoparts.erp.auth.dto.UserRoleRequest;
import com.autoparts.erp.auth.dto.UserStatusRequest;
import com.autoparts.erp.auth.dto.UserUpdateRequest;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.service.RefreshTokenService;
import com.autoparts.erp.auth.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("ADMIN", "MANAGER", "CASHIER");

    private final UserManagementRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    public UserServiceImpl(
            UserManagementRepository repository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    // =========================================================
    // CREATE USER
    // =========================================================

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {

        String username = request.username().trim();

        Set<String> roles = normalizeRoles(request.roleNames());

        if (repository.existsByUsername(username, null)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        UUID uuid = UUID.randomUUID();

        Long userId = repository.create(
                username,
                passwordHash,
                uuid
        );

        repository.replaceRoles(userId, roles);

        UserResponse response = findByUuid(uuid);

        auditLogService.log(
                "CREATE",
                "USER",
                uuid.toString(),
                "User account created",
                null,
                userAuditValues(response)
        );

        return response;
    }

    // =========================================================
    // FIND ALL USERS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {

        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // FIND USER BY UUID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByUuid(UUID uuid) {

        ManagedUser user = getRequiredUser(uuid);

        return toResponse(user);
    }

    // =========================================================
    // UPDATE USERNAME
    // =========================================================

    @Override
    @Transactional
    public UserResponse update(
            UUID uuid,
            UserUpdateRequest request
    ) {

        ManagedUser existing = getRequiredUser(uuid);

        String username = request.username().trim();

        if (repository.existsByUsername(username, uuid)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        Map<String, Object> oldValues =
                userAuditValues(toResponse(existing));

        repository.updateUsername(uuid, username);

        UserResponse updated = findByUuid(uuid);

        auditLogService.log(
                "UPDATE",
                "USER",
                uuid.toString(),
                "Username updated",
                oldValues,
                userAuditValues(updated)
        );

        return updated;
    }

    // =========================================================
    // ENABLE / DISABLE USER
    // =========================================================

    @Override
    @Transactional
    public UserResponse updateStatus(
            UUID uuid,
            UserStatusRequest request
    ) {

        ManagedUser existing = getRequiredUser(uuid);

        boolean enabled = request.enabled();

        if (existing.enabled() == enabled) {
            return toResponse(existing);
        }

        if (!enabled) {

            Long currentUserId =
                    currentUserService.getCurrentUserId();

            if (existing.id().equals(currentUserId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "You cannot deactivate your own account"
                );
            }

            if (repository.isAdmin(existing.id())) {

                // Lock all active ADMIN rows to prevent concurrent
                // changes from removing the last active administrator.
                repository.lockActiveAdminUsers();

                if (repository.countActiveAdmins() <= 1) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The last active administrator cannot be deactivated"
                    );
                }
            }
        }

        Map<String, Object> oldValues =
                userAuditValues(toResponse(existing));

        repository.updateEnabled(uuid, enabled);

        UserResponse updated = findByUuid(uuid);

        auditLogService.log(
                "UPDATE",
                "USER",
                uuid.toString(),
                enabled ? "User account enabled" : "User account disabled",
                oldValues,
                userAuditValues(updated)
        );

        if (!enabled) {
            // Revoke all existing refresh tokens for this account.
            refreshTokenService.revokeAllForUser(existing.id());
        }

        return updated;
    }

    // =========================================================
    // ASSIGN / CHANGE ROLES
    // =========================================================

    @Override
    @Transactional
    public UserResponse updateRoles(
            UUID uuid,
            UserRoleRequest request
    ) {

        ManagedUser existing = getRequiredUser(uuid);

        Long currentUserId =
                currentUserService.getCurrentUserId();

        if (existing.id().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You cannot change your own roles"
            );
        }

        Set<String> newRoles = normalizeRoles(request.roleNames());

        boolean currentlyAdmin =
                existing.roles().contains("ADMIN");

        boolean remainsAdmin = newRoles.contains("ADMIN");

        if (existing.enabled() && currentlyAdmin && !remainsAdmin) {

            repository.lockActiveAdminUsers();

            if (repository.countActiveAdmins() <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The last active administrator cannot lose the ADMIN role"
                );
            }
        }

        Map<String, Object> oldValues =
                userAuditValues(toResponse(existing));

        repository.replaceRoles(existing.id(), newRoles);

        UserResponse updated = findByUuid(uuid);

        auditLogService.log(
                "UPDATE",
                "USER",
                uuid.toString(),
                "User roles updated",
                oldValues,
                userAuditValues(updated)
        );

        return updated;
    }

    // =========================================================
    // RESET PASSWORD
    // =========================================================

    @Override
    @Transactional
    public void resetPassword(
            UUID uuid,
            UserPasswordResetRequest request
    ) {

        ManagedUser existing = getRequiredUser(uuid);

        String passwordHash =
                passwordEncoder.encode(request.newPassword());

        repository.updatePassword(uuid, passwordHash);
        userRepository.resetFailedLoginAttempts(existing.username());
        // Revoke all refresh tokens so the user must authenticate again.
        refreshTokenService.revokeAllForUser(existing.id());

        auditLogService.log(
                "UPDATE",
                "USER",
                uuid.toString(),
                "User password reset by administrator",
                null,
                Map.of("passwordReset", true)
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private ManagedUser getRequiredUser(UUID uuid) {

        return repository.findByUuid(uuid)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );
    }

    private UserResponse toResponse(ManagedUser user) {

        return new UserResponse(
                user.uuid(),
                user.username(),
                user.enabled(),
                user.roles(),
                user.createdAt(),
                user.updatedAt()
        );
    }

    private Set<String> normalizeRoles(Set<String> roleNames) {

        if (roleNames == null || roleNames.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one role is required"
            );
        }

        Set<String> normalized = new LinkedHashSet<>();

        for (String role : roleNames) {

            if (role == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid role"
                );
            }

            String value = role.trim().toUpperCase(Locale.ROOT);

            if (!ALLOWED_ROLES.contains(value)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported role: " + value
                );
            }

            normalized.add(value);
        }

        return Set.copyOf(normalized);
    }

    private Map<String, Object> userAuditValues(UserResponse user) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("username", user.username());
        values.put("enabled", user.enabled());
        values.put("roles", user.roles());

        return values;
    }
}
