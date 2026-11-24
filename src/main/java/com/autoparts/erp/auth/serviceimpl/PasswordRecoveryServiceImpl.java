package com.autoparts.erp.auth.serviceimpl;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.dto.ChangePasswordRequest;
import com.autoparts.erp.auth.dto.ForgotPasswordOtpRequest;
import com.autoparts.erp.auth.dto.ResetPasswordRequest;
import com.autoparts.erp.auth.dto.VerifyEmailRequest;
import com.autoparts.erp.auth.dto.VerifyOtpRequest;
import com.autoparts.erp.auth.dto.VerifyOtpResponse;
import com.autoparts.erp.auth.model.SecurityTokenPurpose;
import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.model.UserSecurityToken;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.repository.UserSecurityTokenRepository;
import com.autoparts.erp.auth.service.PasswordRecoveryService;
import com.autoparts.erp.auth.service.RefreshTokenService;
import com.autoparts.erp.auth.util.TokenCryptoUtils;
import com.autoparts.erp.email.service.EmailService;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);

    private final UserRepository userRepository;
    private final UserManagementRepository userManagementRepository;
    private final UserSecurityTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    private final int otpExpirationMinutes;
    private final int otpMaxAttempts;
    private final int resendCooldownSeconds;
    private final int resetTokenExpirationMinutes;
    private final int emailVerificationExpirationMinutes;

    public PasswordRecoveryServiceImpl(
            UserRepository userRepository,
            UserManagementRepository userManagementRepository,
            UserSecurityTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditLogService auditLogService,
            @Value("${app.security.otp.expiration-minutes:5}") int otpExpirationMinutes,
            @Value("${app.security.otp.max-attempts:5}") int otpMaxAttempts,
            @Value("${app.security.otp.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${app.security.password-reset.token-expiration-minutes:15}") int resetTokenExpirationMinutes,
            @Value("${app.security.email-verification.token-expiration-minutes:1440}") int emailVerificationExpirationMinutes
    ) {
        this.userRepository = userRepository;
        this.userManagementRepository = userManagementRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.resetTokenExpirationMinutes = resetTokenExpirationMinutes;
        this.emailVerificationExpirationMinutes = emailVerificationExpirationMinutes;
    }

    // =========================================================================
    // 1. FORGOT PASSWORD: REQUEST OTP
    // =========================================================================

    @Override
    @Transactional
    public void requestPasswordResetOtp(ForgotPasswordOtpRequest request, String clientIp) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> userOpt = userRepository.findByEmail(normalizedEmail);

        // Do not reveal whether email exists or is verified (anti-enumeration)
        if (userOpt.isEmpty()) {
            log.info("Password reset OTP requested for unregistered email; returning generic response");
            return;
        }

        UserAccount user = userOpt.get();
        if (user.email() == null || !user.emailVerified() || !user.enabled()) {
            log.info("Password reset OTP requested for unverified or disabled user '{}'; returning generic response", user.username());
            return;
        }

        // Check resend cooldown
        Optional<UserSecurityToken> latestTokenOpt = tokenRepository.findLatestActiveToken(user.id(), SecurityTokenPurpose.PASSWORD_RESET_OTP);
        if (latestTokenOpt.isPresent() && latestTokenOpt.get().isResendCooldownActive()) {
            log.warn("Password reset OTP request in cooldown for user '{}'", user.username());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another verification code.");
        }

        // Invalidate earlier active OTPs for password reset
        tokenRepository.invalidateAllForUserAndPurpose(user.id(), SecurityTokenPurpose.PASSWORD_RESET_OTP);

        String otp = TokenCryptoUtils.generate6DigitOtp();
        String tokenHash = TokenCryptoUtils.hashToken(otp);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(otpExpirationMinutes));
        Instant resendAvailableAt = Instant.now().plus(Duration.ofSeconds(resendCooldownSeconds));

        tokenRepository.createToken(
                UUID.randomUUID(),
                user.id(),
                tokenHash,
                SecurityTokenPurpose.PASSWORD_RESET_OTP,
                expiresAt,
                otpMaxAttempts,
                resendAvailableAt
        );

        emailService.sendPasswordResetOtp(user.email(), user.username(), otp, otpExpirationMinutes);

        auditLogService.logSystem(
                "SYSTEM",
                "CREATE",
                "USER",
                user.username(),
                "Password reset verification code requested",
                null,
                Map.of("emailMasked", true, "clientIp", clientIp != null ? clientIp : "unknown")
        );
    }

    // =========================================================================
    // 2. FORGOT PASSWORD: VERIFY OTP
    // =========================================================================

    @Override
    @Transactional
    public VerifyOtpResponse verifyPasswordResetOtp(VerifyOtpRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty() || !userOpt.get().emailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code.");
        }

        UserAccount user = userOpt.get();
        Optional<UserSecurityToken> tokenOpt = tokenRepository.findLatestActiveToken(user.id(), SecurityTokenPurpose.PASSWORD_RESET_OTP);

        if (tokenOpt.isEmpty() || tokenOpt.get().isExpired() || tokenOpt.get().isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code.");
        }

        UserSecurityToken token = tokenOpt.get();
        if (token.isMaxAttemptsExceeded()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum verification attempts exceeded. Please request a new code.");
        }

        String hashedOtp = TokenCryptoUtils.hashToken(request.otp().trim());
        if (!TokenCryptoUtils.constantTimeEquals(token.tokenHash(), hashedOtp)) {
            tokenRepository.incrementAttempts(token.id());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code.");
        }

        // Atomically consume the OTP
        boolean consumed = tokenRepository.consumeToken(token.id());
        if (!consumed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code has already been used.");
        }

        // Issue single-use password reset authorization token
        String rawResetToken = TokenCryptoUtils.generateSecureToken(32);
        String resetTokenHash = TokenCryptoUtils.hashToken(rawResetToken);
        Instant resetExpiresAt = Instant.now().plus(Duration.ofMinutes(resetTokenExpirationMinutes));

        tokenRepository.createToken(
                UUID.randomUUID(),
                user.id(),
                resetTokenHash,
                SecurityTokenPurpose.PASSWORD_RESET_AUTHORIZATION,
                resetExpiresAt,
                1,
                null
        );

        return new VerifyOtpResponse(rawResetToken, resetTokenExpirationMinutes * 60L);
    }

    // =========================================================================
    // 3. FORGOT PASSWORD: RESET PASSWORD
    // =========================================================================

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirm password do not match.");
        }

        String hashedResetToken = TokenCryptoUtils.hashToken(request.resetToken().trim());
        Optional<UserSecurityToken> tokenOpt = tokenRepository.findActiveToken(hashedResetToken, SecurityTokenPurpose.PASSWORD_RESET_AUTHORIZATION);

        if (tokenOpt.isEmpty() || tokenOpt.get().isExpired() || tokenOpt.get().isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token.");
        }

        UserSecurityToken token = tokenOpt.get();
        boolean consumed = tokenRepository.consumeToken(token.id());
        if (!consumed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password reset token has already been used.");
        }

        UserAccount user = userRepository.findById(token.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));

        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must not be the same as the current password.");
        }

        // Invalidate any other recovery tokens for this user
        tokenRepository.invalidateAllForUserAndPurpose(user.id(), SecurityTokenPurpose.PASSWORD_RESET_OTP);
        tokenRepository.invalidateAllForUserAndPurpose(user.id(), SecurityTokenPurpose.PASSWORD_RESET_AUTHORIZATION);

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        userRepository.updatePassword(user.id(), newPasswordHash);

        // Revoke all existing refresh tokens
        refreshTokenService.revokeAllForUser(user.id());

        auditLogService.logSystem(
                "SYSTEM",
                "UPDATE",
                "USER",
                user.username(),
                "User password reset via email OTP recovery",
                null,
                Map.of("passwordReset", true)
        );

        if (user.email() != null && user.emailVerified()) {
            emailService.sendPasswordChangeNotification(user.email(), user.username());
        }
    }

    // =========================================================================
    // 4. AUTHENTICATED CHANGE PASSWORD
    // =========================================================================

    @Override
    @Transactional
    public void changePassword(Long currentUserId, ChangePasswordRequest request) {
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirm password do not match.");
        }

        UserAccount user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));

        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must not be the same as the current password.");
        }

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        userRepository.updatePassword(user.id(), newPasswordHash);

        // Invalidate any pending reset tokens
        tokenRepository.invalidateAllForUserAndPurpose(user.id(), SecurityTokenPurpose.PASSWORD_RESET_OTP);
        tokenRepository.invalidateAllForUserAndPurpose(user.id(), SecurityTokenPurpose.PASSWORD_RESET_AUTHORIZATION);

        // Revoke all refresh tokens
        refreshTokenService.revokeAllForUser(user.id());

        auditLogService.log(
                "UPDATE",
                "USER",
                user.username(),
                "User changed own password",
                null,
                Map.of("passwordChanged", true)
        );

        if (user.email() != null && user.emailVerified()) {
            emailService.sendPasswordChangeNotification(user.email(), user.username());
        }
    }

    // =========================================================================
    // 5. EMAIL VERIFICATION
    // =========================================================================

    @Override
    @Transactional
    public void requestEmailVerification(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));

        if (user.email() == null || user.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No email address registered for this account.");
        }

        if (user.emailVerified()) {
            return;
        }

        Optional<UserSecurityToken> latestTokenOpt = tokenRepository.findLatestActiveToken(user.id(), SecurityTokenPurpose.EMAIL_VERIFICATION);
        if (latestTokenOpt.isPresent() && latestTokenOpt.get().isResendCooldownActive()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another verification code.");
        }

        tokenRepository.invalidateAllForUserAndPurpose(user.id(), SecurityTokenPurpose.EMAIL_VERIFICATION);

        String code = TokenCryptoUtils.generate6DigitOtp();
        String tokenHash = TokenCryptoUtils.hashToken(code);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(emailVerificationExpirationMinutes));
        Instant resendAvailableAt = Instant.now().plus(Duration.ofSeconds(resendCooldownSeconds));

        tokenRepository.createToken(
                UUID.randomUUID(),
                user.id(),
                tokenHash,
                SecurityTokenPurpose.EMAIL_VERIFICATION,
                expiresAt,
                otpMaxAttempts,
                resendAvailableAt
        );

        emailService.sendEmailVerification(user.email(), user.username(), code, emailVerificationExpirationMinutes);

        auditLogService.log(
                "CREATE",
                "USER",
                user.username(),
                "Email verification requested",
                null,
                Map.of("emailMasked", true)
        );
    }

    @Override
    @Transactional
    public void confirmEmailVerification(VerifyEmailRequest request) {
        String hashedToken = TokenCryptoUtils.hashToken(request.token().trim());
        Optional<UserSecurityToken> tokenOpt = tokenRepository.findActiveToken(hashedToken, SecurityTokenPurpose.EMAIL_VERIFICATION);

        if (tokenOpt.isEmpty() || tokenOpt.get().isExpired() || tokenOpt.get().isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired email verification code.");
        }

        UserSecurityToken token = tokenOpt.get();
        boolean consumed = tokenRepository.consumeToken(token.id());
        if (!consumed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code has already been used.");
        }

        userManagementRepository.setEmailVerified(token.userId(), true);

        UserAccount user = userRepository.findById(token.userId()).orElse(null);
        String username = user != null ? user.username() : String.valueOf(token.userId());

        auditLogService.log(
                "UPDATE",
                "USER",
                username,
                "User email address verified successfully",
                null,
                Map.of("emailVerified", true)
        );
    }
}
