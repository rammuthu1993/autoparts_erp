
package com.autoparts.erp.auth.serviceimpl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.autoparts.erp.auth.dto.LoginRequest;
import com.autoparts.erp.auth.dto.LoginResponse;
import com.autoparts.erp.auth.service.AuthService;
import com.autoparts.erp.security.JwtService;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger =
            LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim(),
                            request.password()));
        } catch (AuthenticationException exception) {
            logger.warn(
                "Login attempt failed: {}",
                exception.getClass().getSimpleName()
            );
    throw exception;
}
        // catch (AuthenticationException exception) {
        //     logger.warn("Login attempt failed");
        //     throw exception;
        // }

        String token = jwtService.generateAccessToken(authentication);
        logger.info("Login succeeded");

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                authentication.getName()
        );
    }
}
