
package com.autoparts.erp.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.autoparts.erp.security.ratelimit.AuthRateLimitFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final AuthRateLimitFilter authRateLimitFilter;

    public SecurityConfig(
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler,
            AuthRateLimitFilter authRateLimitFilter
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.authRateLimitFilter = authRateLimitFilter;
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

            .cors(Customizer.withDefaults())

            .csrf(csrf -> csrf.disable())

            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .requestCache(requestCache -> requestCache.disable())

            .formLogin(form -> form.disable())

            .httpBasic(basic -> basic.disable())

            .logout(logout -> logout.disable())

            .addFilterBefore(
                new SecurityCorrelationFilter(),
                SecurityContextHolderFilter.class
            )

            .addFilterAfter(
                authRateLimitFilter,
                SecurityCorrelationFilter.class
            )

            .authorizeHttpRequests(auth -> auth

                // Allow Spring Boot error dispatches.
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll()

                // Public authentication & recovery endpoints.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/forgot-password/**",
                    "/api/v1/auth/verify-email/confirm"
                ).permitAll()

                // Public health and info endpoints (including liveness and readiness probes).
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/health",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()

                // Phase P3.1: Public OpenAPI 3 / Swagger UI documentation endpoints.
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**"
                ).permitAll()

                // Phase P3.2: ADMIN-only Prometheus metrics and actuator management endpoints.
                .requestMatchers(
                    "/actuator/prometheus",
                    "/actuator/metrics/**",
                    "/actuator/**"
                ).hasRole("ADMIN")

                // Authenticated user can query their own effective permissions
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/admin/users/me/permissions"
                ).authenticated()

                // Manager & Admin can access user password reset (hierarchical policy enforced in service)
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/v1/admin/users/*/password"
                ).hasAnyRole("ADMIN", "MANAGER")

                // ADMIN-only management and administration endpoints.
                .requestMatchers(
                    "/api/v1/admin/**",
                    "/api/v1/users/**",
                    "/api/v1/roles/**",
                    "/api/v1/audit-logs/**"
                ).hasRole("ADMIN")

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
    public AuthenticationManager authenticationManager(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:http://localhost:4200,http://localhost:5173}")
            String allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> preventDoubleAuthRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
