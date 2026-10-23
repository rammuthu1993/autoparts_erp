package com.autoparts.erp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import com.autoparts.erp.auth.dto.LoginRequest;
import com.autoparts.erp.auth.dto.LoginResponse;
import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.serviceimpl.AuthServiceImpl;
import com.autoparts.erp.config.JwtConfig;

class AuthenticationFlowTests {

    private static final String ISSUER = "autoparts-erp";
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "test-only-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

    @Test
    void successfulLoginAuthenticatesAndIssuesIssuerValidatedJwt() {
        JwtConfig jwtConfig = new JwtConfig();
        SecretKey key = jwtConfig.jwtSecretKey(TEST_SECRET);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(key);
        var jwtDecoder = jwtConfig.jwtDecoder(key, ISSUER);
        var passwordEncoder = new BCryptPasswordEncoder(4);
        UserRepository userRepository = username -> username.equalsIgnoreCase("alice")
                ? Optional.of(new UserAccount(
                        1L,
                        "alice",
                        passwordEncoder.encode("correct-password"),
                        true,
                        List.of("ROLE_USER", "ROLE_ADMIN")))
                : Optional.empty();

        CustomUserDetailsService userDetailsService =
                new CustomUserDetailsService(userRepository);
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        var authenticationManager = new ProviderManager(provider);
        JwtService jwtService = new JwtService(jwtEncoder, ISSUER, 900);
        AuthServiceImpl authService =
                new AuthServiceImpl(authenticationManager, jwtService);

        LoginResponse response = authService.login(
                new LoginRequest(" alice ", "correct-password"));
        Jwt decodedToken = jwtDecoder.decode(response.accessToken());

        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresInSeconds());
        assertEquals("alice", response.username());
        assertEquals(ISSUER, decodedToken.getClaimAsString("iss"));
        assertEquals("alice", decodedToken.getSubject());
        assertEquals(
                Set.of("ROLE_USER", "ROLE_ADMIN"),
                Set.copyOf(decodedToken.getClaimAsStringList("roles")));
    }

    @Test
    void invalidPasswordIsRejectedWithoutIssuingAToken() {
        var passwordEncoder = new BCryptPasswordEncoder(4);
        UserRepository userRepository = username -> Optional.of(new UserAccount(
                1L,
                username,
                passwordEncoder.encode("correct-password"),
                true,
                List.of("ROLE_USER")));
        CustomUserDetailsService userDetailsService =
                new CustomUserDetailsService(userRepository);
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        var authService = new AuthServiceImpl(
                new ProviderManager(provider),
                new JwtService(
                        new JwtConfig().jwtEncoder(
                                new JwtConfig().jwtSecretKey(TEST_SECRET)),
                        ISSUER,
                        900));

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(new LoginRequest("alice", "incorrect")));
    }

    @Test
    void disabledAccountCannotAuthenticate() {
        var passwordEncoder = new BCryptPasswordEncoder(4);
        UserRepository userRepository = username -> Optional.of(new UserAccount(
                1L,
                username,
                passwordEncoder.encode("correct-password"),
                false,
                List.of("ROLE_USER")));
        CustomUserDetailsService userDetailsService =
                new CustomUserDetailsService(userRepository);
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        assertThrows(
                DisabledException.class,
                () -> new ProviderManager(provider).authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                "alice",
                                "correct-password")));
    }

    @Test
    void decoderRejectsTokenWithDifferentIssuer() {
        JwtConfig jwtConfig = new JwtConfig();
        SecretKey key = jwtConfig.jwtSecretKey(TEST_SECRET);
        JwtEncoder encoder = jwtConfig.jwtEncoder(key);
        var decoder = jwtConfig.jwtDecoder(key, ISSUER);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("untrusted-issuer")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .subject("alice")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoderRejectsSecretShorterThanRequiredLength() {
        String shortSecret = Base64.getEncoder().encodeToString(
                "too-short".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                IllegalStateException.class,
                () -> new JwtConfig().jwtSecretKey(shortSecret));
    }

    @Test
    void rolesClaimConverterIgnoresBlankAndDuplicateAuthorities() {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("roles", List.of("ROLE_USER", " ", "ROLE_USER", "ROLE_ADMIN"))
                .build();

        List<GrantedAuthority> authorities =
                new SecurityConfig.RolesClaimConverter().convert(token).stream().toList();

        assertEquals(
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")),
                authorities);
        assertFalse(authorities.isEmpty());
        assertTrue(authorities.stream()
                .noneMatch(authority -> authority.getAuthority().isBlank()));
    }
}
