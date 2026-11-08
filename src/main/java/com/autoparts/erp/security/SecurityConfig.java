
package com.autoparts.erp.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.convert.converter.Converter;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    // =========================================================
    // 1. JWT Authentication Converter Bean
    // =========================================================

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                new RolesClaimConverter()
        );

        return converter;
    }

    // =========================================================
    // 2. Security Filter Chain
    // =========================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

            .csrf(csrf -> csrf.disable())

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .requestCache(requestCache -> requestCache.disable())

            .formLogin(form -> form.disable())

            .httpBasic(basic -> basic.disable())

            .logout(logout -> logout.disable())

            .authorizeHttpRequests(auth -> auth

                // Allow Spring Boot error dispatches.
                .dispatcherTypeMatchers(
                    jakarta.servlet.DispatcherType.ERROR
                ).permitAll()

                // Public authentication endpoints.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout"
                ).permitAll()

                // Public health endpoint.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/health",
                    "/actuator/health"
                ).permitAll()

                // ADMIN-only endpoints.
                .requestMatchers("/api/v1/admin/**")
                .hasRole("ADMIN")

                // All other endpoints require authentication.
                .anyRequest().authenticated()
            )

            .exceptionHandling(exceptions -> exceptions

                .authenticationEntryPoint(
                    authenticationEntryPoint
                )

                .accessDeniedHandler(
                    accessDeniedHandler
                )
            )

            .oauth2ResourceServer(oauth2 -> oauth2

                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                )

                .authenticationEntryPoint(
                    authenticationEntryPoint
                )

                .accessDeniedHandler(
                    accessDeniedHandler
                )
            );

        return http.build();
    }

    // =========================================================
    // 3. JWT Roles Claim Converter
    // =========================================================

    static class RolesClaimConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {

            List<String> roles =
                    jwt.getClaimAsStringList("roles");

            if (roles == null || roles.isEmpty()) {
                return List.of();
            }

            return roles.stream()

                    .filter(role ->
                        role != null && !role.isBlank()
                    )

                    .map(String::trim)

                    .map(role ->
                        role.toUpperCase(Locale.ROOT)
                    )

                    // Avoid duplicate ROLE_ prefix.
                    .map(role ->
                        role.startsWith("ROLE_")
                            ? role
                            : "ROLE_" + role
                    )

                    // Accept only configured ERP roles.
                    .filter(role ->
                        role.equals("ROLE_ADMIN")
                        || role.equals("ROLE_MANAGER")
                        || role.equals("ROLE_CASHIER")
                    )

                    .distinct()

                    .map(role ->
                        (GrantedAuthority)
                            new SimpleGrantedAuthority(role)
                    )

                    .toList();
        }
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider) {

        return new ProviderManager(authenticationProvider);
    }
}
