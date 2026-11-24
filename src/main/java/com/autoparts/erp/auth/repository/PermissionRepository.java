package com.autoparts.erp.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.autoparts.erp.auth.model.Permission;

public interface PermissionRepository {

    List<Permission> findAll(String module, String search);

    Optional<Permission> findByUuid(UUID uuid);

    Optional<Permission> findByCode(String code);

    List<Permission> findByCodes(Collection<String> codes);

    List<Permission> findByRoleId(Long roleId);

    List<Permission> findByRoleUuid(UUID roleUuid);

    Set<String> findPermissionCodesByRoleId(Long roleId);

    Set<String> findPermissionCodesByRoleNames(Collection<String> roleNames);

    Set<String> findPermissionCodesByUserId(Long userId);

    Set<String> findRoleNamesByUserId(Long userId);

    Optional<Long> findRoleIdByUuid(UUID roleUuid);

    Optional<String> findRoleNameByUuid(UUID roleUuid);

    void replaceRolePermissions(Long roleId, Collection<Long> permissionIds, Long assignedByUserId);
}
