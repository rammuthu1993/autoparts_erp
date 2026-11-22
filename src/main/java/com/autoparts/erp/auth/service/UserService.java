
package com.autoparts.erp.auth.service;

import java.util.List;
import java.util.UUID;

import com.autoparts.erp.auth.dto.UserCreateRequest;
import com.autoparts.erp.auth.dto.UserPasswordResetRequest;
import com.autoparts.erp.auth.dto.UserResponse;
import com.autoparts.erp.auth.dto.UserRoleRequest;
import com.autoparts.erp.auth.dto.UserStatusRequest;
import com.autoparts.erp.auth.dto.UserUpdateRequest;
import com.autoparts.erp.common.dto.PageResponse;

public interface UserService {

    UserResponse create(UserCreateRequest request);

    List<UserResponse> findAll();

    PageResponse<UserResponse> findPaged(
        int page,
        int size,
        String search,
        String sortBy,
        String sortDir
    );

    UserResponse findByUuid(UUID uuid);

    UserResponse update(UUID uuid, UserUpdateRequest request);

    UserResponse updateStatus(UUID uuid, UserStatusRequest request);

    UserResponse updateRoles(UUID uuid, UserRoleRequest request);

    void resetPassword(UUID uuid, UserPasswordResetRequest request);
}
