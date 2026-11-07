
package com.autoparts.erp.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;

    private final Collection<? extends GrantedAuthority> authorities;

    private final boolean enabled;
    private final boolean accountNonLocked;

    public AuthenticatedUserDetails(
            Long userId,
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean enabled,
            boolean accountNonLocked
    ) {

        this.userId = userId;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
