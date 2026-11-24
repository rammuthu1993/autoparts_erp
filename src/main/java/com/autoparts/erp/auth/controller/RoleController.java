package com.autoparts.erp.auth.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.auth.dto.AssignRolePermissionsRequest;
import com.autoparts.erp.auth.dto.RolePermissionsResponse;
import com.autoparts.erp.auth.dto.RoleResponse;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.auth.service.RoleService;

@RestController
@RequestMapping("/api/v1/admin/roles")
@AdminOnly
public class RoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    public RoleController(RoleService roleService, PermissionService permissionService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    @GetMapping
    @RequiresPermission("ROLES.VIEW")
    public ResponseEntity<List<RoleResponse>> findAll() {
        return ResponseEntity.ok(roleService.findAll());
    }

    @GetMapping("/{uuid}")
    @RequiresPermission("ROLES.VIEW")
    public ResponseEntity<RoleResponse> findByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(roleService.findByUuid(uuid));
    }

    @GetMapping("/{uuid}/permissions")
    @RequiresPermission("ROLES.VIEW")
    public ResponseEntity<RolePermissionsResponse> getRolePermissions(@PathVariable UUID uuid) {
        return ResponseEntity.ok(permissionService.getRolePermissions(uuid));
    }

    @PutMapping("/{uuid}/permissions")
    @RequiresPermission("ROLES.PERMISSIONS_ASSIGN")
    public ResponseEntity<RolePermissionsResponse> assignRolePermissions(
            @PathVariable UUID uuid,
            @Valid @RequestBody AssignRolePermissionsRequest request
    ) {
        permissionService.assignRolePermissions(uuid, request);
        return ResponseEntity.ok(permissionService.getRolePermissions(uuid));
    }
}
