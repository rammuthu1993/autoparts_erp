
package com.autoparts.erp.auth.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.auth.dto.ChangePasswordRequest;
import com.autoparts.erp.auth.dto.ForgotPasswordOtpRequest;
import com.autoparts.erp.auth.dto.GenericMessageResponse;
import com.autoparts.erp.auth.dto.LoginRequest;
import com.autoparts.erp.auth.dto.LoginResponse;
import com.autoparts.erp.auth.dto.RefreshTokenRequest;
import com.autoparts.erp.auth.dto.ResetPasswordRequest;
import com.autoparts.erp.auth.dto.VerifyEmailRequest;
import com.autoparts.erp.auth.dto.VerifyOtpRequest;
import com.autoparts.erp.auth.dto.VerifyOtpResponse;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.service.AuthService;
import com.autoparts.erp.auth.service.PasswordRecoveryService;
import com.autoparts.erp.auth.service.RefreshTokenService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenService refreshTokenService;
	private final PasswordRecoveryService passwordRecoveryService;
	private final CurrentUserService currentUserService;

	public AuthController(
			AuthService authService,
			RefreshTokenService refreshTokenService,
			PasswordRecoveryService passwordRecoveryService,
			CurrentUserService currentUserService
	) {
		this.authService = authService;
		this.refreshTokenService = refreshTokenService;
		this.passwordRecoveryService = passwordRecoveryService;
		this.currentUserService = currentUserService;
	}

	@SecurityRequirements
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@SecurityRequirements
	@PostMapping("/refresh")
	public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {

		RefreshTokenService.RotatedTokens tokens = refreshTokenService.refresh(request.refreshToken())
				.orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token."));

		LoginResponse response = new LoginResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
				tokens.expiresInSeconds(), tokens.refreshExpiresInSeconds(), tokens.username());

		return ResponseEntity.ok(response);
	}

	@SecurityRequirements
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {

		refreshTokenService.revokeRefreshToken(request.refreshToken());

		return ResponseEntity.noContent().build();
	}

	@PostMapping("/change-password")
	public ResponseEntity<GenericMessageResponse> changePassword(
			@Valid @RequestBody ChangePasswordRequest request
	) {
		Long currentUserId = currentUserService.getCurrentUserId();
		passwordRecoveryService.changePassword(currentUserId, request);
		return ResponseEntity.ok(new GenericMessageResponse("Password changed successfully."));
	}

	@SecurityRequirements
	@PostMapping("/forgot-password/request-otp")
	public ResponseEntity<GenericMessageResponse> requestPasswordResetOtp(
			@Valid @RequestBody ForgotPasswordOtpRequest request,
			HttpServletRequest servletRequest
	) {
		String clientIp = servletRequest.getRemoteAddr();
		passwordRecoveryService.requestPasswordResetOtp(request, clientIp);
		return ResponseEntity.ok(new GenericMessageResponse("If the email is registered and verified, a password reset code has been sent."));
	}

	@SecurityRequirements
	@PostMapping("/forgot-password/verify-otp")
	public ResponseEntity<VerifyOtpResponse> verifyPasswordResetOtp(
			@Valid @RequestBody VerifyOtpRequest request
	) {
		VerifyOtpResponse response = passwordRecoveryService.verifyPasswordResetOtp(request);
		return ResponseEntity.ok(response);
	}

	@SecurityRequirements
	@PostMapping("/forgot-password/reset")
	public ResponseEntity<GenericMessageResponse> resetPassword(
			@Valid @RequestBody ResetPasswordRequest request
	) {
		passwordRecoveryService.resetPassword(request);
		return ResponseEntity.ok(new GenericMessageResponse("Password has been reset successfully. Please log in with your new password."));
	}

	@PostMapping("/verify-email/request")
	public ResponseEntity<GenericMessageResponse> requestEmailVerification() {
		Long currentUserId = currentUserService.getCurrentUserId();
		passwordRecoveryService.requestEmailVerification(currentUserId);
		return ResponseEntity.ok(new GenericMessageResponse("Verification code sent to your email."));
	}

	@SecurityRequirements
	@PostMapping("/verify-email/confirm")
	public ResponseEntity<GenericMessageResponse> confirmEmailVerification(
			@Valid @RequestBody VerifyEmailRequest request
	) {
		passwordRecoveryService.confirmEmailVerification(request);
		return ResponseEntity.ok(new GenericMessageResponse("Email has been verified successfully."));
	}
}
