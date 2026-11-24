package com.autoparts.erp.auth.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;

import com.autoparts.erp.auth.dto.AssignRolePermissionsRequest;
import com.autoparts.erp.auth.dto.PermissionResponse;
import com.autoparts.erp.auth.dto.RolePermissionsResponse;
import com.autoparts.erp.auth.dto.UserEffectivePermissionsResponse;

public interface PermissionService {

    boolean hasPermission(Authentication authentication, String permissionCode);

    boolean hasPermission(Long userId, String permissionCode);

    boolean hasAnyPermission(Authentication authentication, String... permissionCodes);

    Set<String> getEffectivePermissionsForUser(Long userId);

    Set<String> getEffectivePermissionsForCurrentUser();

    UserEffectivePermissionsResponse getCurrentUserEffectivePermissions();

    UserEffectivePermissionsResponse getUserEffectivePermissions(UUID userUuid);

    List<PermissionResponse> getAllPermissions(String module, String search);

    PermissionResponse getPermissionByUuid(UUID uuid);

    RolePermissionsResponse getRolePermissions(UUID roleUuid);

    void assignRolePermissions(UUID roleUuid, AssignRolePermissionsRequest request);

    void evictUserRoleCache(Long userId);

    void evictRolePermissionCache(Long roleId);

    void evictAllCaches();

    void invalidateAllCaches();
}
