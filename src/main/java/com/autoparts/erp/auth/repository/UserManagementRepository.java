
package com.autoparts.erp.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.autoparts.erp.auth.model.ManagedUser;

public interface UserManagementRepository {

    List<ManagedUser> findAll();

    List<ManagedUser> findPaged(
        int offset,
        int limit,
        String search,
        String sortBy,
        String sortDir
    );

    long countPaged(String search);

    Optional<ManagedUser> findByUuid(UUID uuid);

    Optional<ManagedUser> findById(Long id);

    boolean existsByUsername(String username, UUID excludeUuid);

    boolean existsByEmail(String email, UUID excludeUuid);

    Long create(String username, String email, String passwordHash, UUID uuid);

    default Long create(String username, String passwordHash, UUID uuid) {
        return create(username, null, passwordHash, uuid);
    }

    void updateUsername(UUID uuid, String username);

    void updateEmail(UUID uuid, String email, boolean emailVerified);

    void updateUsernameAndEmail(UUID uuid, String username, String email, boolean emailVerified);

    void setEmailVerified(Long userId, boolean emailVerified);

    void updatePassword(UUID uuid, String passwordHash);

    void updateEnabled(UUID uuid, boolean enabled);

    void replaceRoles(Long userId, Set<String> roleNames);

    List<Long> lockActiveAdminUsers();

    long countActiveAdmins();

    boolean isAdmin(Long userId);
}
