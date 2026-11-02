
package com.autoparts.erp.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserAccount account = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "Invalid username or password."
            ));

        List<SimpleGrantedAuthority> authorities =
            account.roles()
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new AuthenticatedUserDetails(
            account.id(),
            account.username(),
            account.passwordHash(),
            authorities,
            account.enabled()
        );
    }
}
