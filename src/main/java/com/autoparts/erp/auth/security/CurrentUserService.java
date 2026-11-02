
package com.autoparts.erp.auth.security;

import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private static final Logger log =
        LoggerFactory.getLogger(CurrentUserService.class);

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long getCurrentUserId() {

        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {

            throw new AuthenticationCredentialsNotFoundException(
                "Authenticated user information is unavailable"
            );
        }

        String username = authentication.getName();

        UserAccount account = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn(
                    "Authenticated username does not exist in the user repository"
                );

                return new AuthenticationCredentialsNotFoundException(
                    "Authenticated user could not be found"
                );
            });

        return account.id();
    }
}
