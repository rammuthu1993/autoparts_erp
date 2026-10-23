
package com.autoparts.erp.auth.repository;

import java.util.Optional;

import com.autoparts.erp.auth.model.UserAccount;

public interface UserRepository {

    Optional<UserAccount> findByUsername(String username);
}
