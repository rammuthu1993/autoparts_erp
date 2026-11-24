package com.autoparts.erp.auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.auth.dto.PermissionResponse;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.service.PermissionService;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@AdminOnly
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @RequiresPermission("PERMISSIONS.VIEW")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(permissionService.getAllPermissions(module, search));
    }

    @GetMapping("/{uuid}")
    @RequiresPermission("PERMISSIONS.VIEW")
    public ResponseEntity<PermissionResponse> getPermissionByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(permissionService.getPermissionByUuid(uuid));
    }
}
