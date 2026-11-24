package com.autoparts.erp.auth.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.autoparts.erp.auth.dto.UserCreateRequest;
import com.autoparts.erp.auth.dto.UserEffectivePermissionsResponse;
import com.autoparts.erp.auth.dto.UserPasswordResetRequest;
import com.autoparts.erp.auth.dto.UserResponse;
import com.autoparts.erp.auth.dto.UserRoleRequest;
import com.autoparts.erp.auth.dto.UserStatusRequest;
import com.autoparts.erp.auth.dto.UserUpdateRequest;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.AuthenticatedUser;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.auth.service.UserService;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

	private final UserService userService;
	private final PermissionService permissionService;

	public UserController(UserService userService, PermissionService permissionService) {
		this.userService = userService;
		this.permissionService = permissionService;
	}

	// =========================================================
	// CURRENT USER EFFECTIVE PERMISSIONS (ANY AUTHENTICATED USER)
	// =========================================================

	@GetMapping("/me/permissions")
	@AuthenticatedUser
	public ResponseEntity<UserEffectivePermissionsResponse> getCurrentUserPermissions() {
		return ResponseEntity.ok(permissionService.getCurrentUserEffectivePermissions());
	}

	// =========================================================
	// USER PERMISSIONS BY UUID (ADMIN ONLY)
	// =========================================================

	@GetMapping("/{uuid}/permissions")
	@AdminOnly
	@RequiresPermission("USERS.VIEW")
	public ResponseEntity<UserEffectivePermissionsResponse> getUserPermissions(@PathVariable UUID uuid) {
		return ResponseEntity.ok(permissionService.getUserEffectivePermissions(uuid));
	}

	// =========================================================
	// CREATE USER
	// =========================================================

	@PostMapping
	@AdminOnly
	@RequiresPermission("USERS.CREATE")
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {

		UserResponse response = userService.create(request);

		return ResponseEntity.created(URI.create("/api/v1/admin/users/" + response.uuid())).body(response);
	}

	// =========================================================
	// GET ALL USERS
	// =========================================================

	@Deprecated(since = "2.0")
	@DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/admin/users/paged", deprecationDate = "2026-11-22T00:00:00Z")
	@GetMapping
	@AdminOnly
	@RequiresPermission("USERS.VIEW")
	public ResponseEntity<List<UserResponse>> findAll() {

		return ResponseEntity.ok(userService.findAll());
	}

	@GetMapping("/paged")
	@AdminOnly
	@RequiresPermission("USERS.VIEW")
	public ResponseEntity<PageResponse<UserResponse>> findPaged(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "asc") String sortDir) {
		return ResponseEntity.ok(userService.findPaged(page, size, search, sortBy, sortDir));
	}

	// =========================================================
	// GET USER BY UUID
	// =========================================================

	@GetMapping("/{uuid}")
	@AdminOnly
	@RequiresPermission("USERS.VIEW")
	public ResponseEntity<UserResponse> findByUuid(@PathVariable UUID uuid) {

		return ResponseEntity.ok(userService.findByUuid(uuid));
	}

	// =========================================================
	// UPDATE USERNAME / EMAIL
	// =========================================================

	@PutMapping("/{uuid}")
	@AdminOnly
	@RequiresPermission("USERS.UPDATE")
	public ResponseEntity<UserResponse> update(@PathVariable UUID uuid, @Valid @RequestBody UserUpdateRequest request) {

		return ResponseEntity.ok(userService.update(uuid, request));
	}

	// =========================================================
	// ENABLE / DISABLE USER
	// =========================================================

	@PatchMapping("/{uuid}/status")
	@AdminOnly
	@RequiresPermission("USERS.STATUS_UPDATE")
	public ResponseEntity<UserResponse> updateStatus(@PathVariable UUID uuid,
			@Valid @RequestBody UserStatusRequest request) {

		return ResponseEntity.ok(userService.updateStatus(uuid, request));
	}

	// =========================================================
	// ASSIGN / CHANGE ROLES
	// =========================================================

	@PutMapping("/{uuid}/roles")
	@AdminOnly
	@RequiresPermission("USERS.ROLES_ASSIGN")
	public ResponseEntity<UserResponse> updateRoles(@PathVariable UUID uuid,
			@Valid @RequestBody UserRoleRequest request) {

		return ResponseEntity.ok(userService.updateRoles(uuid, request));
	}

	// =========================================================
	// RESET PASSWORD (ADMIN OR MANAGER HIERARCHICAL RESET)
	// =========================================================

	@PatchMapping("/{uuid}/password")
	@AdminOrManager
	@RequiresPermission("USERS.RESET_PASSWORD")
	public ResponseEntity<Void> resetPassword(@PathVariable UUID uuid,
			@Valid @RequestBody UserPasswordResetRequest request) {

		userService.resetPassword(uuid, request);

		return ResponseEntity.noContent().build();
	}
}
