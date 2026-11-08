
package com.autoparts.erp.auth.service;

import java.util.List;
import java.util.UUID;

import com.autoparts.erp.auth.dto.RoleResponse;

public interface RoleService {

    List<RoleResponse> findAll();

    RoleResponse findByUuid(UUID uuid);
}
