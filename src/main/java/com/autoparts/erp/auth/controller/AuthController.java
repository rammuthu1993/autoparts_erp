
package com.autoparts.erp.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.auth.dto.LoginRequest;
import com.autoparts.erp.auth.dto.LoginResponse;
import com.autoparts.erp.auth.dto.RefreshTokenRequest;
import com.autoparts.erp.auth.service.AuthService;
import com.autoparts.erp.auth.service.RefreshTokenService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService
    ) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {

        RefreshTokenService.RotatedTokens tokens =
                refreshTokenService.refresh(request.refreshToken())
                        .orElseThrow(() ->
                                new BadCredentialsException(
                                        "Invalid or expired refresh token."
                                )
                        );

        LoginResponse response = new LoginResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.expiresInSeconds(),
                tokens.refreshExpiresInSeconds(),
                tokens.username()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {

        refreshTokenService.revokeRefreshToken(
                request.refreshToken()
        );

        return ResponseEntity.noContent().build();
    }
}
