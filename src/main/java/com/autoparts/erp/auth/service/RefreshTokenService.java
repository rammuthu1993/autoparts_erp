
package com.autoparts.erp.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.auth.model.RefreshToken;
import com.autoparts.erp.auth.repository.RefreshTokenRepository;
import com.autoparts.erp.security.JwtService;
import com.autoparts.erp.security.CustomUserDetailsService;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final CustomUserDetailsService userDetailsService;

    private final JwtService jwtService;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Duration refreshTokenLifetime;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            CustomUserDetailsService userDetailsService,
            JwtService jwtService,
            @Value("${app.security.refresh-token.expiration-seconds}")
            long expirationSeconds
    ) {

        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Refresh token expiration must be positive."
            );
        }

        this.refreshTokenRepository = refreshTokenRepository;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenLifetime =
                Duration.ofSeconds(expirationSeconds);
    }

    /**
     * Creates the initial refresh token after login.
     */
    @Transactional
    public IssuedRefreshToken createRefreshToken(Long userId) {

        String rawToken = generateSecureToken();

        String tokenHash = hashToken(rawToken);

        UUID tokenFamily = UUID.randomUUID();

        Instant expiresAt = Instant.now()
                .plus(refreshTokenLifetime);

        refreshTokenRepository.insert(
                userId,
                tokenHash,
                tokenFamily,
                expiresAt
        );

        return new IssuedRefreshToken(
                rawToken,
                refreshTokenLifetime.toSeconds()
        );
    }

    /**
     * Rotates the refresh token.
     *
     * Returns empty when token is invalid, expired,
     * revoked, reused, or belongs to an inactive account.
     */
    @Transactional
    public Optional<RotatedTokens> refresh(String rawToken) {

        if (rawToken == null || rawToken.isBlank()
                || rawToken.length() > 256) {
            return Optional.empty();
        }

        String tokenHash = hashToken(rawToken);

        Optional<RefreshToken> optionalToken =
                refreshTokenRepository.findByTokenHashForUpdate(
                        tokenHash
                );

        if (optionalToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken existingToken = optionalToken.get();

        Instant now = Instant.now();

        // Reuse of a revoked token:
        // revoke the entire token family.
        if (existingToken.revokedAt() != null) {

            refreshTokenRepository.revokeTokenFamily(
                    existingToken.tokenFamily(),
                    now
            );

            return Optional.empty();
        }

        // Expired token.
        if (!existingToken.expiresAt().isAfter(now)) {

            refreshTokenRepository.revokeTokenFamily(
                    existingToken.tokenFamily(),
                    now
            );

            return Optional.empty();
        }

        UserDetails userDetails;

        try {

            userDetails = userDetailsService.loadUserByUsername(
                    existingToken.username()
            );

        } catch (UsernameNotFoundException exception) {

            refreshTokenRepository.revokeTokenFamily(
                    existingToken.tokenFamily(),
                    now
            );

            return Optional.empty();
        }

        // Do not issue tokens for disabled or locked accounts.
        if (!userDetails.isEnabled()
                || !userDetails.isAccountNonLocked()) {

            refreshTokenRepository.revokeTokenFamily(
                    existingToken.tokenFamily(),
                    now
            );

            return Optional.empty();
        }

        // Generate the next refresh token.
        String newRawToken = generateSecureToken();

        String newTokenHash = hashToken(newRawToken);

        // Revoke the previous token and link its replacement.
        int updated = refreshTokenRepository.revokeAndReplace(
                existingToken.id(),
                now,
                newTokenHash
        );

        if (updated != 1) {
            return Optional.empty();
        }

        // Keep the same token family during rotation.
        Instant newExpiresAt = now.plus(refreshTokenLifetime);

        refreshTokenRepository.insert(
                existingToken.userId(),
                newTokenHash,
                existingToken.tokenFamily(),
                newExpiresAt
        );

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        String newAccessToken =
                jwtService.generateAccessToken(authentication);

        return Optional.of(
                new RotatedTokens(
                        newAccessToken,
                        newRawToken,
                        jwtService.getExpirationSeconds(),
                        refreshTokenLifetime.toSeconds(),
                        userDetails.getUsername()
                )
        );
    }

    /**
     * Logout: revoke the complete token family.
     * Repeated logout requests are harmless.
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()
                || rawToken.length() > 256) {
            return;
        }

        String tokenHash = hashToken(rawToken);

        refreshTokenRepository.revokeTokenFamilyByTokenHash(
                tokenHash,
                Instant.now()
        );
    }

    private String generateSecureToken() {

        byte[] randomBytes = new byte[32];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String rawToken) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return java.util.HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable.",
                    exception
            );
        }
    }

    public record IssuedRefreshToken(
            String refreshToken,
            long expiresInSeconds
    ) {
    }

    public record RotatedTokens(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            long refreshExpiresInSeconds,
            String username
    ) {
    }
}
