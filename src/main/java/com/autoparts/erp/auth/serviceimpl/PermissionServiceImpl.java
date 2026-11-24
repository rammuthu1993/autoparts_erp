package com.autoparts.erp.auth.serviceimpl;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.exception.ClientValidationException;
import com.autoparts.erp.auth.dto.AssignRolePermissionsRequest;
import com.autoparts.erp.auth.dto.PermissionResponse;
import com.autoparts.erp.auth.dto.RolePermissionsResponse;
import com.autoparts.erp.auth.dto.UserEffectivePermissionsResponse;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.model.Permission;
import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repository.PermissionRepository;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.security.AuthenticatedUserDetails;

@Service("permissionService")
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private final PermissionRepository permissionRepository;
    private final UserManagementRepository userManagementRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    // Cache: roleId -> Set<String> of active permission codes
    private final ConcurrentMap<Long, Set<String>> rolePermissionsCache = new ConcurrentHashMap<>();

    // Cache: userId -> CacheEntry<Set<String>> of active role names with TTL
    private final ConcurrentMap<Long, CacheEntry<Set<String>>> userRolesCache = new ConcurrentHashMap<>();

    private static final Duration USER_ROLE_CACHE_TTL = Duration.ofSeconds(60);

    private record CacheEntry<T>(T data, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public PermissionServiceImpl(
            PermissionRepository permissionRepository,
            UserManagementRepository userManagementRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService
    ) {
        this.permissionRepository = permissionRepository;
        this.userManagementRepository = userManagementRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    // =========================================================================
    // 1. DYNAMIC PERMISSION CHECKING (WITH LIVE ROLE & PERMISSION FRESHNESS)
    // =========================================================================

    @Override
    public boolean hasPermission(Authentication authentication, String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return false;
        }

        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        Long userId = resolveUserId(authentication);
        if (userId == null) {
            log.warn("Unable to resolve userId for authentication: {}", authentication.getName());
            return false;
        }

        return hasPermission(userId, permissionCode);
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        String normalizedCode = permissionCode.trim().toUpperCase();

        // Live active roles from database/cache
        Set<String> activeRoles = getActiveUserRoles(userId);
        if (activeRoles.isEmpty()) {
            return false;
        }

        // ADMIN role possesses all system permissions by definition
        if (activeRoles.contains("ADMIN") || activeRoles.contains("ROLE_ADMIN")) {
            return true;
        }

        // Resolve effective permissions across all active user roles
        Set<String> effectivePermissions = getPermissionsForRoles(activeRoles);
        return effectivePermissions.contains(normalizedCode);
    }

    @Override
    public boolean hasAnyPermission(Authentication authentication, String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        for (String code : permissionCodes) {
            if (hasPermission(authentication, code)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getEffectivePermissionsForUser(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> roles = getActiveUserRoles(userId);
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }
        if (roles.contains("ADMIN") || roles.contains("ROLE_ADMIN")) {
            return permissionRepository.findAll(null, null).stream()
                    .filter(Permission::enabled)
                    .map(Permission::code)
                    .collect(Collectors.toSet());
        }
        return getPermissionsForRoles(roles);
    }

    @Override
    public Set<String> getEffectivePermissionsForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = resolveUserId(auth);
        if (userId == null) {
            return Collections.emptySet();
        }
        return getEffectivePermissionsForUser(userId);
    }

    @Override
    public UserEffectivePermissionsResponse getCurrentUserEffectivePermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long userId = resolveUserId(auth);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        ManagedUser user = userManagementRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        Set<String> roles = getActiveUserRoles(userId);
        Set<String> permissions = getEffectivePermissionsForUser(userId);

        return new UserEffectivePermissionsResponse(
                user.uuid(),
                user.username(),
                roles,
                permissions
        );
    }

    @Override
    public UserEffectivePermissionsResponse getUserEffectivePermissions(UUID userUuid) {
        ManagedUser user = userManagementRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        Set<String> roles = getActiveUserRoles(user.id());
        Set<String> permissions = getEffectivePermissionsForUser(user.id());

        return new UserEffectivePermissionsResponse(
                user.uuid(),
                user.username(),
                roles,
                permissions
        );
    }

    // =========================================================================
    // 2. PERMISSION CATALOGUE & ROLE ASSIGNMENT MANAGEMENT
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions(String module, String search) {
        return permissionRepository.findAll(module, search)
                .stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionByUuid(UUID uuid) {
        return permissionRepository.findByUuid(uuid)
                .map(PermissionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(UUID roleUuid) {
        Long roleId = permissionRepository.findRoleIdByUuid(roleUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        String roleName = permissionRepository.findRoleNameByUuid(roleUuid).orElse("ROLE");

        List<PermissionResponse> permissions = permissionRepository.findByRoleId(roleId)
                .stream()
                .map(PermissionResponse::from)
                .toList();

        return new RolePermissionsResponse(
                roleUuid,
                roleName,
                "Configured permissions for role " + roleName,
                permissions
        );
    }

    @Override
    @Transactional
    public void assignRolePermissions(UUID roleUuid, AssignRolePermissionsRequest request) {
        if (request == null || request.permissionCodes() == null || request.permissionCodes().isEmpty()) {
            throw new ClientValidationException("At least one permission must be assigned to the role");
        }

        Long roleId = permissionRepository.findRoleIdByUuid(roleUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        String roleName = permissionRepository.findRoleNameByUuid(roleUuid).orElse("ROLE");

        Set<String> requestedCodes = request.permissionCodes().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        // Validate all submitted permission codes exist and are enabled
        List<Permission> foundPermissions = permissionRepository.findByCodes(requestedCodes);
        Set<String> foundCodes = foundPermissions.stream()
                .map(Permission::code)
                .collect(Collectors.toSet());

        Set<String> invalidCodes = new HashSet<>(requestedCodes);
        invalidCodes.removeAll(foundCodes);
        if (!invalidCodes.isEmpty()) {
            throw new ClientValidationException(
                    "Invalid, unknown, or disabled permission code(s): " + invalidCodes
            );
        }

        // Safety Defense: Prevent stripping critical administrative permissions from the ADMIN role
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            Set<String> criticalAdminPerms = Set.of(
                    "ROLES.PERMISSIONS_ASSIGN",
                    "USERS.ROLES_ASSIGN",
                    "USERS.STATUS_UPDATE"
            );
            if (!requestedCodes.containsAll(criticalAdminPerms)) {
                throw new ClientValidationException(
                        "Cannot remove critical permissions (e.g. ROLES.PERMISSIONS_ASSIGN, USERS.ROLES_ASSIGN) from the ADMIN role."
                );
            }
        }

        // Old values for audit trail
        Set<String> oldPermCodes = permissionRepository.findPermissionCodesByRoleId(roleId);

        // Assign permissions
        Long assignedByUserId = currentUserService.getCurrentUserId();
        List<Long> permIds = foundPermissions.stream().map(Permission::id).toList();
        permissionRepository.replaceRolePermissions(roleId, permIds, assignedByUserId);

        // Invalidate caches immediately
        evictRolePermissionCache(roleId);

        // Record audit trail
        auditLogService.log(
                "UPDATE",
                "ROLE",
                roleUuid.toString(),
                "Role permissions updated for role " + roleName,
                Map.of("permissions", oldPermCodes),
                Map.of("permissions", requestedCodes)
        );
    }

    // =========================================================================
    // 3. CACHE MANAGEMENT & INVALIDATION
    // =========================================================================

    @Override
    public void evictUserRoleCache(Long userId) {
        if (userId != null) {
            userRolesCache.remove(userId);
        }
    }

    @Override
    public void evictRolePermissionCache(Long roleId) {
        if (roleId != null) {
            rolePermissionsCache.remove(roleId);
        } else {
            rolePermissionsCache.clear();
        }
        // When role permissions change, any cached user roles should also be refreshed
        userRolesCache.clear();
    }

    @Override
    public void evictAllCaches() {
        rolePermissionsCache.clear();
        userRolesCache.clear();
    }

    @Override
    public void invalidateAllCaches() {
        evictAllCaches();
    }

    // =========================================================================
    // 4. HELPER METHODS
    // =========================================================================

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object userIdClaim = jwt.getClaim("user_id");
            if (userIdClaim instanceof Number n) {
                return n.longValue();
            } else if (userIdClaim instanceof String s) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignored) {}
            }
        } else if (principal instanceof AuthenticatedUserDetails aud) {
            return aud.getUserId();
        }

        // Fallback: resolve from database by username
        String username = authentication.getName();
        if (username != null && !username.isBlank()) {
            return userRepository.findByUsername(username)
                    .map(UserAccount::id)
                    .orElse(null);
        }

        return null;
    }

    private Set<String> getActiveUserRoles(Long userId) {
        CacheEntry<Set<String>> entry = userRolesCache.get(userId);
        if (entry != null && !entry.isExpired()) {
            return entry.data();
        }

        Set<String> liveRoles = permissionRepository.findRoleNamesByUserId(userId);
        userRolesCache.put(userId, new CacheEntry<>(liveRoles, Instant.now().plus(USER_ROLE_CACHE_TTL)));
        return liveRoles;
    }

    private Set<String> getPermissionsForRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Collections.emptySet();
        }

        // Resolve permissions for all roles
        return permissionRepository.findPermissionCodesByRoleNames(roleNames);
    }
}
