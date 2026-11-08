
package com.autoparts.erp.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.autoparts.erp.auth.model.ManagedUser;

public interface UserManagementRepository {

    List<ManagedUser> findAll();

    Optional<ManagedUser> findByUuid(UUID uuid);

    boolean existsByUsername(String username, UUID excludeUuid);

    Long create(String username, String passwordHash, UUID uuid);

    void updateUsername(UUID uuid, String username);

    void updatePassword(UUID uuid, String passwordHash);

    void updateEnabled(UUID uuid, boolean enabled);

    void replaceRoles(Long userId, Set<String> roleNames);

    List<Long> lockActiveAdminUsers();

    long countActiveAdmins();

    boolean isAdmin(Long userId);
}
