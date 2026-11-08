
package com.autoparts.erp.auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.auth.dto.RoleResponse;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.service.RoleService;

@RestController
@RequestMapping("/api/v1/admin/roles")
@AdminOnly
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> findAll() {

        return ResponseEntity.ok(
                roleService.findAll()
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<RoleResponse> findByUuid(
            @PathVariable UUID uuid
    ) {

        return ResponseEntity.ok(
                roleService.findByUuid(uuid)
        );
    }
}
