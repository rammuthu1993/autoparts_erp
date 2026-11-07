
package com.autoparts.erp.auth.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
//import org.springframework.security.authentication.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.autoparts.erp.auth.dto.LoginRequest;
import com.autoparts.erp.auth.dto.LoginResponse;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.service.AuthService;
import com.autoparts.erp.auth.service.RefreshTokenService;
import com.autoparts.erp.security.AuthenticatedUserDetails;
import com.autoparts.erp.security.JwtService;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.LockedException;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger =
            LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

    private final UserRepository userRepository;

    private final int maxFailedAttempts;

    private final long lockDurationSeconds;
    
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${app.security.login.max-failed-attempts:5}")
            int maxFailedAttempts,
            @Value("${app.security.login.lock-duration-seconds:900}")
            long lockDurationSeconds,
            RefreshTokenService refreshTokenService
    ) {

        if (maxFailedAttempts <= 0 || lockDurationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Login security configuration must be positive."
            );
        }

        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {

        String username = request.username().trim();

        Authentication authentication;

        try {

            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            username,
                            request.password()
                    )
            );

        } catch (LockedException exception) {

            logger.warn("Login rejected because account is locked.");

            throw exception;

        } catch (BadCredentialsException exception) {

            userRepository.recordFailedLoginAttempt(
                    username,
                    maxFailedAttempts,
                    lockDurationSeconds
            );

            logger.warn("Login attempt failed.");

            throw exception;

        } catch (AuthenticationException exception) {

            logger.warn(
                    "Authentication rejected: {}",
                    exception.getClass().getSimpleName()
            );

            throw exception;
        }
        // Reset failed attempts after successful authentication.
        userRepository.resetFailedLoginAttempts(
                authentication.getName()
        );

        AuthenticatedUserDetails userDetails =
                (AuthenticatedUserDetails) authentication.getPrincipal();

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.createRefreshToken(
                        userDetails.getUserId()
                );

        String accessToken =
                jwtService.generateAccessToken(authentication);

        logger.info("Login succeeded.");

        return new LoginResponse(
                accessToken,
                refreshToken.refreshToken(),
                "Bearer",
                jwtService.getExpirationSeconds(),
                refreshToken.expiresInSeconds(),
                authentication.getName()
        );
    }
}
