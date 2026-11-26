
package com.autoparts.erp.auth.serviceimpl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.autoparts.erp.auth.repository.PermissionRepository;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.auth.service.RefreshTokenService;
import com.autoparts.erp.auth.service.UserService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.util.PaginationUtils;

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
    private final PermissionService permissionService;
    private final PermissionRepository permissionRepository;

    @Autowired
    public UserServiceImpl(
            UserManagementRepository repository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            PermissionService permissionService,
            PermissionRepository permissionRepository
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.permissionRepository = permissionRepository;
    }

    public UserServiceImpl(
            UserManagementRepository repository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository
    ) {
        this(repository, passwordEncoder, auditLogService, currentUserService, refreshTokenService, userRepository, null, null);
    }

    // =========================================================
    // CREATE USER
    // =========================================================

    @Override
    @Transactional
    @RequiresPermission("USERS.CREATE")
    public UserResponse create(UserCreateRequest request) {

        String username = request.username().trim();
        String email = request.email() != null && !request.email().isBlank()
                ? request.email().trim().toLowerCase(Locale.ROOT)
                : null;

        Set<String> roles = normalizeRoles(request.roleNames());

        if (repository.existsByUsername(username, null)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        if (email != null && repository.existsByEmail(email, null)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        UUID uuid = UUID.randomUUID();

        Long userId = repository.create(
                username,
                email,
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
    @RequiresPermission("USERS.VIEW")
    public List<UserResponse> findAll() {

        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("USERS.VIEW")
    public PageResponse<UserResponse> findPaged(
            int page,
            int size,
            String search,
            String sortBy,
            String sortDir
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<UserResponse> content = repository.findPaged(
                offset,
                safeSize,
                search,
                sortBy,
                sortDir
        ).stream().map(this::toResponse).toList();

        long total = repository.countPaged(search);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    // =========================================================
    // FIND USER BY UUID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("USERS.VIEW")
    public UserResponse findByUuid(UUID uuid) {

        ManagedUser user = getRequiredUser(uuid);

        return toResponse(user);
    }

    // =========================================================
    // UPDATE USERNAME
    // =========================================================

    @Override
    @Transactional
    @RequiresPermission("USERS.UPDATE")
    public UserResponse update(
            UUID uuid,
            UserUpdateRequest request
    ) {

        ManagedUser existing = getRequiredUser(uuid);

        String username = request.username().trim();
        String newEmail = request.email() != null && !request.email().isBlank()
                ? request.email().trim().toLowerCase(Locale.ROOT)
                : null;

        if (repository.existsByUsername(username, uuid)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        boolean emailChanged = false;
        if (newEmail != null && !newEmail.equalsIgnoreCase(existing.email())) {
            if (repository.existsByEmail(newEmail, uuid)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Email already registered"
                );
            }
            emailChanged = true;
        }

        Map<String, Object> oldValues =
                userAuditValues(toResponse(existing));

        if (newEmail != null) {
            boolean emailVerified = emailChanged ? false : existing.emailVerified();
            repository.updateUsernameAndEmail(uuid, username, newEmail, emailVerified);
        } else {
            repository.updateUsername(uuid, username);
        }

        UserResponse updated = findByUuid(uuid);

        auditLogService.log(
                "UPDATE",
                "USER",
                uuid.toString(),
                emailChanged ? "User profile and email updated" : "Username updated",
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
    @RequiresPermission("USERS.STATUS_UPDATE")
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

        if (permissionService != null) {
            permissionService.evictUserRoleCache(existing.id());
        }

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
    @RequiresPermission("USERS.ROLES_ASSIGN")
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

        if (permissionService != null) {
            permissionService.evictUserRoleCache(existing.id());
        }

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
    @RequiresPermission("USERS.RESET_PASSWORD")
    public void resetPassword(
            UUID uuid,
            UserPasswordResetRequest request
    ) {

        ManagedUser existing = getRequiredUser(uuid);

        if (currentUserService != null) {
            Long currentUserId = currentUserService.getCurrentUserId();

            // Self-reset prevention: Cannot use administrative reset on oneself
            if (currentUserId != null && currentUserId.equals(existing.id())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Self-reset is not allowed through the administrative reset endpoint. Use the change-password endpoint instead."
                );
            }

            if (permissionRepository != null) {
                Set<String> callerRoles = permissionRepository.findRoleNamesByUserId(currentUserId);
                Set<String> targetRoles = existing.roles();

                boolean callerIsAdmin = callerRoles.contains("ADMIN");
                boolean callerIsManager = callerRoles.contains("MANAGER");

                if (callerIsAdmin) {
                    // Administrator may reset subordinate users
                } else if (callerIsManager) {
                    // Manager may only reset Cashier passwords (cannot reset Admin or Manager)
                    if (targetRoles.contains("ADMIN") || targetRoles.contains("MANAGER")) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Managers may only reset passwords for Cashiers"
                        );
                    }
                } else {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Access denied"
                    );
                }
            }
        }

        String passwordHash =
                passwordEncoder.encode(request.newPassword());

        repository.updatePassword(uuid, passwordHash);
        userRepository.resetFailedLoginAttempts(existing.username());
        // Revoke all refresh tokens so the user must authenticate again.
        refreshTokenService.revokeAllForUser(existing.id());

        if (permissionService != null) {
            permissionService.evictUserRoleCache(existing.id());
        }

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
                user.email(),
                user.emailVerified(),
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
        values.put("email", user.email());
        values.put("emailVerified", user.emailVerified());
        values.put("enabled", user.enabled());
        values.put("roles", user.roles());

        return values;
    }
}
