package com.autoparts.erp.auth.service;

import com.autoparts.erp.auth.dto.ChangePasswordRequest;
import com.autoparts.erp.auth.dto.ForgotPasswordOtpRequest;
import com.autoparts.erp.auth.dto.ResetPasswordRequest;
import com.autoparts.erp.auth.dto.VerifyEmailRequest;
import com.autoparts.erp.auth.dto.VerifyOtpRequest;
import com.autoparts.erp.auth.dto.VerifyOtpResponse;

public interface PasswordRecoveryService {

    void requestPasswordResetOtp(ForgotPasswordOtpRequest request, String clientIp);

    VerifyOtpResponse verifyPasswordResetOtp(VerifyOtpRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(Long currentUserId, ChangePasswordRequest request);

    void requestEmailVerification(Long userId);

    void confirmEmailVerification(VerifyEmailRequest request);
}
