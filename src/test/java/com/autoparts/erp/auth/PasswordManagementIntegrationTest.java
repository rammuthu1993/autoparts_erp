package com.autoparts.erp.auth;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.dto.ChangePasswordRequest;
import com.autoparts.erp.auth.dto.ForgotPasswordOtpRequest;
import com.autoparts.erp.auth.dto.ResetPasswordRequest;
import com.autoparts.erp.auth.dto.UserCreateRequest;
import com.autoparts.erp.auth.dto.UserResponse;
import com.autoparts.erp.auth.dto.VerifyEmailRequest;
import com.autoparts.erp.auth.dto.VerifyOtpRequest;
import com.autoparts.erp.auth.dto.VerifyOtpResponse;
import com.autoparts.erp.auth.model.SecurityTokenPurpose;
import com.autoparts.erp.auth.model.UserAccount;
import com.autoparts.erp.auth.model.UserSecurityToken;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.repository.UserSecurityTokenRepository;
import com.autoparts.erp.auth.service.RefreshTokenService;
import com.autoparts.erp.auth.service.UserService;
import com.autoparts.erp.auth.util.TokenCryptoUtils;
import com.autoparts.erp.email.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {AutopartsErpApplication.class, PasswordManagementIntegrationTest.TestEmailConfig.class})
@DisplayName("Three-Tier Password Management Integration Tests (Change Password, Forgot Password, Admin Reset)")
class PasswordManagementIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/autoparts_erp_test";
    private static final String ISSUER = "autoparts-erp";

    private static final AtomicReference<String> LAST_CAPTURED_OTP = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CAPTURED_VERIFICATION_TOKEN = new AtomicReference<>();

    @TestConfiguration
    static class TestEmailConfig {
        @Bean
        @Primary
        public EmailService mockEmailService() {
            return new EmailService() {
                @Override
                public void sendPasswordResetOtp(String toEmail, String username, String otp, int validityMinutes) {
                    LAST_CAPTURED_OTP.set(otp);
                }

                @Override
                public void sendEmailVerification(String toEmail, String username, String verificationToken, int validityMinutes) {
                    LAST_CAPTURED_VERIFICATION_TOKEN.set(verificationToken);
                }

                @Override
                public void sendPasswordChangeNotification(String toEmail, String username) {
                    // notification sent
                }
            };
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUser = resolveSecret("DB_USERNAME", "postgres");
        String dbPass = resolveSecret("DB_PASSWORD", "postgres");
        String aiReaderPass = resolveSecret("AI_TOOL_DB_PASSWORD", "Muthu@1512");

        registry.add("DB_URL", () -> TEST_DB_URL);
        registry.add("DB_USERNAME", () -> dbUser);
        registry.add("DB_PASSWORD", () -> dbPass);

        registry.add("AI_TOOL_DB_URL", () -> TEST_DB_URL);
        registry.add("AI_TOOL_DB_USERNAME", () -> "autoparts_ai_reader");
        registry.add("AI_TOOL_DB_PASSWORD", () -> aiReaderPass);

        String validBase64Secret = Base64.getEncoder().encodeToString(
                "SecurityTestJwtSecretMustBeAtLeast32BytesLong123456789".getBytes(StandardCharsets.UTF_8)
        );
        registry.add("JWT_SECRET", () -> validBase64Secret);
        registry.add("SARVAM_API_KEY", () -> "test-sarvam-key-placeholder");
        registry.add("APP_CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000,http://localhost:8080");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.mail.enabled", () -> "true");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserManagementRepository userManagementRepository;

    @Autowired
    private UserSecurityTokenRepository tokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("jdbcTemplate")
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        jdbcTemplate.execute("""
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(254) NULL;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
            CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (LOWER(TRIM(email))) WHERE email IS NOT NULL;

            CREATE TABLE IF NOT EXISTS user_security_tokens (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                token_hash VARCHAR(128) NOT NULL,
                purpose VARCHAR(50) NOT NULL,
                expires_at TIMESTAMPTZ NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                max_attempts INTEGER NOT NULL DEFAULT 5,
                resend_available_at TIMESTAMPTZ NULL,
                consumed_at TIMESTAMPTZ NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_user_security_tokens_user_purpose ON user_security_tokens (user_id, purpose) WHERE consumed_at IS NULL;
            CREATE INDEX IF NOT EXISTS idx_user_security_tokens_hash_purpose ON user_security_tokens (token_hash, purpose) WHERE consumed_at IS NULL;
            CREATE INDEX IF NOT EXISTS idx_user_security_tokens_expires_at ON user_security_tokens (expires_at);
        """);

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
        LAST_CAPTURED_OTP.set(null);
        LAST_CAPTURED_VERIFICATION_TOKEN.set(null);
    }

    // =========================================================================
    // WORKFLOW A: AUTHENTICATED CHANGE PASSWORD
    // =========================================================================

    @Test
    @DisplayName("Change Password: authenticated user successfully changes own password; refresh tokens revoked")
    void testChangePasswordSuccess() throws Exception {
        String username = "cp_user_" + UUID.randomUUID().toString().substring(0, 6);
        String initialPassword = "OldPassword12345!";
        String newPassword = "NewPassword12345!";

        UserResponse user = userService.create(new UserCreateRequest(
                username,
                username + "@example.com",
                initialPassword,
                Set.of("CASHIER")
        ));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        Long userId = account.id();

        // Issue a refresh token to verify it gets revoked
        RefreshTokenService.IssuedRefreshToken issuedRefreshToken = refreshTokenService.createRefreshToken(userId);

        String userToken = createBearerToken(username, List.of("ROLE_CASHIER"), userId);

        ChangePasswordRequest request = new ChangePasswordRequest(
                initialPassword,
                newPassword,
                newPassword
        );

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Password changed successfully")));

        // Verify password hash updated in DB
        UserAccount updatedAccount = userRepository.findById(userId).orElseThrow();
        assertTrue(passwordEncoder.matches(newPassword, updatedAccount.passwordHash()));
        assertFalse(passwordEncoder.matches(initialPassword, updatedAccount.passwordHash()));

        // Verify refresh token was revoked
        assertTrue(refreshTokenService.refresh(issuedRefreshToken.refreshToken()).isEmpty());
    }

    @Test
    @DisplayName("Change Password: rejects incorrect current password with 400 Bad Request")
    void testChangePasswordIncorrectCurrentPassword() throws Exception {
        String username = "cp_user_" + UUID.randomUUID().toString().substring(0, 6);
        String initialPassword = "CorrectPassword12345!";

        UserResponse user = userService.create(new UserCreateRequest(
                username,
                username + "@example.com",
                initialPassword,
                Set.of("CASHIER")
        ));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        String userToken = createBearerToken(username, List.of("ROLE_CASHIER"), account.id());

        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongCurrentPassword123!",
                "NewPassword12345!",
                "NewPassword12345!"
        );

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Current password is incorrect.")));
    }

    @Test
    @DisplayName("Change Password: rejects password confirmation mismatch with 400 Bad Request")
    void testChangePasswordConfirmationMismatch() throws Exception {
        String username = "cp_user_" + UUID.randomUUID().toString().substring(0, 6);
        String initialPassword = "Password12345!";

        UserResponse user = userService.create(new UserCreateRequest(
                username,
                username + "@example.com",
                initialPassword,
                Set.of("CASHIER")
        ));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        String userToken = createBearerToken(username, List.of("ROLE_CASHIER"), account.id());

        ChangePasswordRequest request = new ChangePasswordRequest(
                initialPassword,
                "NewPassword12345!",
                "DifferentPassword123!"
        );

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("New password and confirm password do not match.")));
    }

    @Test
    @DisplayName("Change Password: rejects reuse of current password with 400 Bad Request")
    void testChangePasswordReuseCurrentPassword() throws Exception {
        String username = "cp_user_" + UUID.randomUUID().toString().substring(0, 6);
        String initialPassword = "Password12345!";

        UserResponse user = userService.create(new UserCreateRequest(
                username,
                username + "@example.com",
                initialPassword,
                Set.of("CASHIER")
        ));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        String userToken = createBearerToken(username, List.of("ROLE_CASHIER"), account.id());

        ChangePasswordRequest request = new ChangePasswordRequest(
                initialPassword,
                initialPassword,
                initialPassword
        );

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("New password must not be the same as the current password.")));
    }

    @Test
    @DisplayName("Change Password: rejects unauthenticated requests with 401 Unauthorized")
    void testChangePasswordUnauthenticated() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword12345!",
                "NewPassword12345!",
                "NewPassword12345!"
        );

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // WORKFLOW B: FORGOT PASSWORD (REQUEST OTP, VERIFY OTP, RESET PASSWORD)
    // =========================================================================

    @Test
    @DisplayName("Forgot Password: full flow (request OTP -> verify OTP -> reset password)")
    void testForgotPasswordFullFlow() throws Exception {
        String username = "fp_user_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        String initialPassword = "InitialPassword12345!";
        String newPassword = "ResetPassword12345!";

        UserResponse created = userService.create(new UserCreateRequest(
                username,
                email,
                initialPassword,
                Set.of("CASHIER")
        ));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        Long userId = account.id();

        // Mark email verified for password recovery eligibility
        userManagementRepository.setEmailVerified(userId, true);

        // Issue a refresh token to verify it gets revoked upon password reset
        RefreshTokenService.IssuedRefreshToken issuedRefreshToken = refreshTokenService.createRefreshToken(userId);

        // 1. Request OTP
        ForgotPasswordOtpRequest otpRequest = new ForgotPasswordOtpRequest(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If the email is registered and verified")));

        String capturedOtp = LAST_CAPTURED_OTP.get();
        assertNotNull(capturedOtp);
        assertEquals(6, capturedOtp.length());

        // 2. Verify OTP
        VerifyOtpRequest verifyRequest = new VerifyOtpRequest(email, capturedOtp);
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").exists())
                .andExpect(jsonPath("$.expiresInSeconds", is(900)))
                .andReturn();

        VerifyOtpResponse verifyResponse = objectMapper.readValue(
                verifyResult.getResponse().getContentAsString(),
                VerifyOtpResponse.class
        );
        String resetToken = verifyResponse.resetToken();
        assertNotNull(resetToken);

        // 3. Reset Password
        ResetPasswordRequest resetRequest = new ResetPasswordRequest(
                resetToken,
                newPassword,
                newPassword
        );

        mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Password has been reset successfully")));

        // 4. Verify password hash updated in DB
        UserAccount updatedAccount = userRepository.findById(userId).orElseThrow();
        assertTrue(passwordEncoder.matches(newPassword, updatedAccount.passwordHash()));
        assertFalse(passwordEncoder.matches(initialPassword, updatedAccount.passwordHash()));

        // 5. Verify refresh token was revoked
        assertTrue(refreshTokenService.refresh(issuedRefreshToken.refreshToken()).isEmpty());
    }

    @Test
    @DisplayName("Forgot Password: unknown and unverified emails return the same generic message (anti-enumeration)")
    void testForgotPasswordAntiEnumeration() throws Exception {
        // Unknown email
        ForgotPasswordOtpRequest unknownReq = new ForgotPasswordOtpRequest("nonexistent.user.12345@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is registered and verified, a password reset code has been sent.")));

        // Unverified email
        String username = "unverified_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        userService.create(new UserCreateRequest(username, email, "Password12345!", Set.of("CASHIER")));

        ForgotPasswordOtpRequest unverifiedReq = new ForgotPasswordOtpRequest(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unverifiedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is registered and verified, a password reset code has been sent.")));
    }

    @Test
    @DisplayName("Forgot Password: enforces 60-second resend cooldown (429 Too Many Requests)")
    void testForgotPasswordResendCooldown() throws Exception {
        String username = "cooldown_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        userService.create(new UserCreateRequest(username, email, "Password12345!", Set.of("CASHIER")));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        userManagementRepository.setEmailVerified(account.id(), true);

        ForgotPasswordOtpRequest otpRequest = new ForgotPasswordOtpRequest(email);

        // First request succeeds
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest)))
                .andExpect(status().isOk());

        // Second request immediately inside 60-second cooldown is rejected with 429
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Please wait before requesting another verification code")));
    }

    @Test
    @DisplayName("Forgot Password: OTP verification rejects invalid code and enforces attempt limit")
    void testOtpVerificationAttemptsAndInvalidCode() throws Exception {
        String username = "otp_attempts_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        userService.create(new UserCreateRequest(username, email, "Password12345!", Set.of("CASHIER")));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        userManagementRepository.setEmailVerified(account.id(), true);

        // Request OTP
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordOtpRequest(email))))
                .andExpect(status().isOk());

        // 4 wrong attempts
        VerifyOtpRequest wrongReq = new VerifyOtpRequest(email, "000000");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Invalid or expired verification code.")));
        }

        // 5th attempt
        mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isBadRequest());

        // 6th attempt: max attempts exceeded
        mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Maximum verification attempts exceeded")));
    }

    @Test
    @DisplayName("Forgot Password: consumed OTP cannot be reused (replay protection)")
    void testOtpReplayProtection() throws Exception {
        String username = "replay_otp_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        userService.create(new UserCreateRequest(username, email, "Password12345!", Set.of("CASHIER")));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        userManagementRepository.setEmailVerified(account.id(), true);

        // Request OTP
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordOtpRequest(email))))
                .andExpect(status().isOk());

        String validOtp = LAST_CAPTURED_OTP.get();
        VerifyOtpRequest verifyRequest = new VerifyOtpRequest(email, validOtp);

        // First verification consumes the OTP
        mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk());

        // Replaying same OTP is rejected with 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Forgot Password: consumed reset token cannot be reused (replay protection)")
    void testResetTokenReplayProtection() throws Exception {
        String username = "replay_token_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        userService.create(new UserCreateRequest(username, email, "Password12345!", Set.of("CASHIER")));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        userManagementRepository.setEmailVerified(account.id(), true);

        // Request OTP
        mockMvc.perform(post("/api/v1/auth/forgot-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordOtpRequest(email))))
                .andExpect(status().isOk());

        // Verify OTP to get reset token
        String validOtp = LAST_CAPTURED_OTP.get();
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/forgot-password/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(email, validOtp))))
                .andExpect(status().isOk())
                .andReturn();

        String resetToken = objectMapper.readValue(verifyResult.getResponse().getContentAsString(), VerifyOtpResponse.class).resetToken();

        ResetPasswordRequest resetReq = new ResetPasswordRequest(resetToken, "NewPass123456!", "NewPass123456!");

        // First reset consumes the token
        mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // Second reset attempt with same token is rejected
        mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid or expired password reset token")));
    }

    // =========================================================================
    // WORKFLOW: EMAIL VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Email Verification: user requests verification and confirms code; marks email_verified = true")
    void testEmailVerificationWorkflow() throws Exception {
        String username = "ev_user_" + UUID.randomUUID().toString().substring(0, 6);
        String email = username + "@example.com";
        userService.create(new UserCreateRequest(username, email, "Password12345!", Set.of("CASHIER")));

        UserAccount account = userRepository.findByUsername(username).orElseThrow();
        assertFalse(account.emailVerified());

        String userToken = createBearerToken(username, List.of("ROLE_CASHIER"), account.id());

        // 1. Request verification
        mockMvc.perform(post("/api/v1/auth/verify-email/request")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Verification code sent")));

        String capturedVerificationCode = LAST_CAPTURED_VERIFICATION_TOKEN.get();
        assertNotNull(capturedVerificationCode);

        // 2. Confirm verification
        VerifyEmailRequest verifyReq = new VerifyEmailRequest(capturedVerificationCode);
        mockMvc.perform(post("/api/v1/auth/verify-email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Email has been verified successfully")));

        // 3. Verify in DB
        UserAccount verifiedAccount = userRepository.findByUsername(username).orElseThrow();
        assertTrue(verifiedAccount.emailVerified());
    }

    private String createBearerToken(String username, List<String> roles, Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(username)
                .claim("roles", roles)
                .claim("user_id", userId)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String resolveSecret(String propertyKey, String defaultVal) {
        String envVal = System.getenv(propertyKey);
        if (envVal != null && !envVal.isBlank()) return envVal;
        Path secretsPath = Path.of("config", "application-secrets.properties");
        if (Files.isRegularFile(secretsPath)) {
            try {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(secretsPath)) {
                    props.load(in);
                }
                if (props.containsKey(propertyKey)) return props.getProperty(propertyKey);
            } catch (Exception ignored) {
            }
        }
        return defaultVal;
    }
}
