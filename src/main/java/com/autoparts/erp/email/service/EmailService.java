package com.autoparts.erp.email.service;

public interface EmailService {

    void sendPasswordResetOtp(String toEmail, String username, String otp, int validityMinutes);

    void sendEmailVerification(String toEmail, String username, String verificationToken, int validityMinutes);

    void sendPasswordChangeNotification(String toEmail, String username);
}
