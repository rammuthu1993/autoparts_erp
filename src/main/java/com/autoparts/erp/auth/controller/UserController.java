
package com.autoparts.erp.auth.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.auth.dto.UserCreateRequest;
import com.autoparts.erp.auth.dto.UserPasswordResetRequest;
import com.autoparts.erp.auth.dto.UserResponse;
import com.autoparts.erp.auth.dto.UserRoleRequest;
import com.autoparts.erp.auth.dto.UserStatusRequest;
import com.autoparts.erp.auth.dto.UserUpdateRequest;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.service.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
@AdminOnly
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // =========================================================
    // CREATE USER
    // =========================================================

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserCreateRequest request
    ) {

        UserResponse response = userService.create(request);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/admin/users/" + response.uuid()
                ))
                .body(response);
    }

    // =========================================================
    // GET ALL USERS
    // =========================================================

    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll() {

        return ResponseEntity.ok(userService.findAll());
    }

    // =========================================================
    // GET USER BY UUID
    // =========================================================

    @GetMapping("/{uuid}")
    public ResponseEntity<UserResponse> findByUuid(
            @PathVariable UUID uuid
    ) {

        return ResponseEntity.ok(
                userService.findByUuid(uuid)
        );
    }

    // =========================================================
    // UPDATE USERNAME
    // =========================================================

    @PutMapping("/{uuid}")
    public ResponseEntity<UserResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserUpdateRequest request
    ) {

        return ResponseEntity.ok(
                userService.update(uuid, request)
        );
    }

    // =========================================================
    // ENABLE / DISABLE USER
    // =========================================================

    @PatchMapping("/{uuid}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserStatusRequest request
    ) {

        return ResponseEntity.ok(
                userService.updateStatus(uuid, request)
        );
    }

    // =========================================================
    // ASSIGN / CHANGE ROLES
    // =========================================================

    @PutMapping("/{uuid}/roles")
    public ResponseEntity<UserResponse> updateRoles(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserRoleRequest request
    ) {

        return ResponseEntity.ok(
                userService.updateRoles(uuid, request)
        );
    }

    // =========================================================
    // RESET PASSWORD
    // =========================================================

    @PatchMapping("/{uuid}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserPasswordResetRequest request
    ) {

        userService.resetPassword(uuid, request);

        return ResponseEntity.noContent().build();
    }
}
