package com.autoparts.erp.email.serviceimpl;

import java.io.UnsupportedEncodingException;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.autoparts.erp.email.config.EmailConfigProperties;
import com.autoparts.erp.email.exception.EmailDeliveryException;
import com.autoparts.erp.email.service.EmailService;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final Optional<JavaMailSender> mailSender;
    private final EmailConfigProperties emailConfig;

    public SmtpEmailService(Optional<JavaMailSender> mailSender, EmailConfigProperties emailConfig) {
        this.mailSender = mailSender;
        this.emailConfig = emailConfig;
    }

    @Override
    public void sendPasswordResetOtp(String toEmail, String username, String otp, int validityMinutes) {
        String subject = "AutoParts ERP - Password Reset Verification Code";
        String htmlBody = buildOtpEmailHtml(username, otp, validityMinutes);
        String textBody = "Hello " + username + ",\n\n"
                + "Your password reset verification code is: " + otp + "\n"
                + "This code is valid for " + validityMinutes + " minutes.\n"
                + "If you did not request this, please ignore this email or contact your ERP administrator.\n\n"
                + "AutoParts ERP Security Team";

        sendEmail(toEmail, subject, htmlBody, textBody);
    }

    @Override
    public void sendEmailVerification(String toEmail, String username, String verificationToken, int validityMinutes) {
        String subject = "AutoParts ERP - Verify Your Email Address";
        String htmlBody = buildVerificationEmailHtml(username, verificationToken, validityMinutes);
        String textBody = "Hello " + username + ",\n\n"
                + "Your email verification code is: " + verificationToken + "\n"
                + "This code is valid for " + validityMinutes + " minutes.\n"
                + "Please verify your email address to enable password recovery.\n\n"
                + "AutoParts ERP Security Team";

        sendEmail(toEmail, subject, htmlBody, textBody);
    }

    @Override
    public void sendPasswordChangeNotification(String toEmail, String username) {
        String subject = "AutoParts ERP - Security Alert: Password Changed";
        String htmlBody = buildPasswordChangeNotificationHtml(username);
        String textBody = "Hello " + username + ",\n\n"
                + "The password for your AutoParts ERP account was recently changed.\n"
                + "If you made this change, no further action is required.\n"
                + "If you did not make this change, please contact your ERP administrator immediately.\n\n"
                + "AutoParts ERP Security Team";

        sendEmail(toEmail, subject, htmlBody, textBody);
    }

    private void sendEmail(String toEmail, String subject, String htmlContent, String textContent) {
        if (!emailConfig.isEnabled()) {
            log.info("Email delivery disabled by configuration; skipping dispatch to {}", maskEmail(toEmail));
            return;
        }

        if (mailSender.isEmpty()) {
            log.warn("JavaMailSender bean is unavailable; skipping dispatch to {}", maskEmail(toEmail));
            return;
        }

        try {
            JavaMailSender sender = mailSender.get();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(emailConfig.getFrom(), emailConfig.getFromName()));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textContent, htmlContent);

            sender.send(message);
            log.info("Successfully sent email with subject '{}' to {}", subject, maskEmail(toEmail));
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.error("Failed to construct or deliver email to {}: {}", maskEmail(toEmail), ex.getMessage());
            throw new EmailDeliveryException("Failed to deliver email. Please try again later or contact your administrator.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during email transmission to {}: {}", maskEmail(toEmail), ex.getMessage());
            throw new EmailDeliveryException("Failed to deliver email. Please try again later or contact your administrator.", ex);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 2) {
            return "**" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    private String buildOtpEmailHtml(String username, String otp, int validityMinutes) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b; }
                .container { max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden; }
                .header { background: #0f172a; padding: 24px; text-align: center; }
                .header h1 { margin: 0; color: #ffffff; font-size: 20px; font-weight: 600; letter-spacing: 0.5px; }
                .content { padding: 32px 24px; }
                .greeting { font-size: 16px; margin-bottom: 16px; font-weight: 500; }
                .otp-box { background: #f1f5f9; border-radius: 8px; padding: 18px; text-align: center; margin: 24px 0; border: 1px solid #cbd5e1; }
                .otp-code { font-size: 32px; font-weight: 700; letter-spacing: 6px; color: #0284c7; font-family: 'Courier New', Courier, monospace; }
                .instructions { font-size: 14px; line-height: 1.6; color: #475569; }
                .warning { font-size: 13px; color: #dc2626; margin-top: 16px; font-weight: 500; }
                .footer { border-top: 1px solid #e2e8f0; padding: 16px 24px; text-align: center; font-size: 12px; color: #94a3b8; background: #fafafa; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>AutoParts ERP</h1>
                </div>
                <div class="content">
                  <div class="greeting">Hello %s,</div>
                  <div class="instructions">
                    We received a request to reset the password for your AutoParts ERP account. Use the verification code below to complete your password reset:
                  </div>
                  <div class="otp-box">
                    <span class="otp-code">%s</span>
                  </div>
                  <div class="instructions">
                    This verification code will expire in <strong>%d minutes</strong>.
                  </div>
                  <div class="warning">
                    If you did not request this password reset, please contact your ERP administrator immediately.
                  </div>
                </div>
                <div class="footer">
                  &copy; AutoParts ERP. Secure Automotive Aftermarket Platform.
                </div>
              </div>
            </body>
            </html>
            """.formatted(escapeHtml(username), escapeHtml(otp), validityMinutes);
    }

    private String buildVerificationEmailHtml(String username, String verificationToken, int validityMinutes) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b; }
                .container { max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden; }
                .header { background: #0f172a; padding: 24px; text-align: center; }
                .header h1 { margin: 0; color: #ffffff; font-size: 20px; font-weight: 600; letter-spacing: 0.5px; }
                .content { padding: 32px 24px; }
                .greeting { font-size: 16px; margin-bottom: 16px; font-weight: 500; }
                .otp-box { background: #f1f5f9; border-radius: 8px; padding: 18px; text-align: center; margin: 24px 0; border: 1px solid #cbd5e1; }
                .otp-code { font-size: 32px; font-weight: 700; letter-spacing: 6px; color: #10b981; font-family: 'Courier New', Courier, monospace; }
                .instructions { font-size: 14px; line-height: 1.6; color: #475569; }
                .footer { border-top: 1px solid #e2e8f0; padding: 16px 24px; text-align: center; font-size: 12px; color: #94a3b8; background: #fafafa; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>AutoParts ERP</h1>
                </div>
                <div class="content">
                  <div class="greeting">Hello %s,</div>
                  <div class="instructions">
                    Please use the following verification code to verify your email address in AutoParts ERP:
                  </div>
                  <div class="otp-box">
                    <span class="otp-code">%s</span>
                  </div>
                  <div class="instructions">
                    This verification code will expire in <strong>%d minutes</strong>.
                  </div>
                </div>
                <div class="footer">
                  &copy; AutoParts ERP. Secure Automotive Aftermarket Platform.
                </div>
              </div>
            </body>
            </html>
            """.formatted(escapeHtml(username), escapeHtml(verificationToken), validityMinutes);
    }

    private String buildPasswordChangeNotificationHtml(String username) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b; }
                .container { max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden; }
                .header { background: #0f172a; padding: 24px; text-align: center; }
                .header h1 { margin: 0; color: #ffffff; font-size: 20px; font-weight: 600; letter-spacing: 0.5px; }
                .content { padding: 32px 24px; }
                .greeting { font-size: 16px; margin-bottom: 16px; font-weight: 500; }
                .instructions { font-size: 14px; line-height: 1.6; color: #475569; }
                .warning { font-size: 13px; color: #dc2626; margin-top: 16px; font-weight: 500; }
                .footer { border-top: 1px solid #e2e8f0; padding: 16px 24px; text-align: center; font-size: 12px; color: #94a3b8; background: #fafafa; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>AutoParts ERP</h1>
                </div>
                <div class="content">
                  <div class="greeting">Hello %s,</div>
                  <div class="instructions">
                    The password for your AutoParts ERP account has just been changed successfully.
                  </div>
                  <div class="warning">
                    If you did NOT perform this action, please contact your ERP administrator immediately to secure your account.
                  </div>
                </div>
                <div class="footer">
                  &copy; AutoParts ERP. Secure Automotive Aftermarket Platform.
                </div>
              </div>
            </body>
            </html>
            """.formatted(escapeHtml(username));
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
