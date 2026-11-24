package com.autoparts.erp.email;

import java.util.Optional;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.autoparts.erp.email.config.EmailConfigProperties;
import com.autoparts.erp.email.exception.EmailDeliveryException;
import com.autoparts.erp.email.serviceimpl.SmtpEmailService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailConfigProperties emailConfig;
    private SmtpEmailService emailService;

    @BeforeEach
    void setUp() {
        emailConfig = new EmailConfigProperties();
        emailConfig.setFrom("noreply@autoparts-erp.com");
        emailConfig.setFromName("AutoParts ERP");
        emailConfig.setEnabled(true);

        emailService = new SmtpEmailService(Optional.of(mailSender), emailConfig);
    }

    @Test
    @DisplayName("sendPasswordResetOtp constructs MIME message and invokes JavaMailSender")
    void testSendPasswordResetOtp() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetOtp("john@example.com", "john", "123456", 5);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    @DisplayName("sendEmailVerification constructs MIME message and invokes JavaMailSender")
    void testSendEmailVerification() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendEmailVerification("jane@example.com", "jane", "654321", 1440);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPasswordChangeNotification constructs security notification email")
    void testSendPasswordChangeNotification() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordChangeNotification("john@example.com", "john");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendEmail throws EmailDeliveryException when JavaMailSender fails")
    void testSendEmailFailureThrowsException() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP connection timed out")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(EmailDeliveryException.class, () ->
                emailService.sendPasswordResetOtp("john@example.com", "john", "123456", 5)
        );
    }

    @Test
    @DisplayName("sendEmail does nothing when mail delivery is disabled via configuration")
    void testEmailDeliveryDisabled() {
        emailConfig.setEnabled(false);

        assertDoesNotThrow(() ->
                emailService.sendPasswordResetOtp("john@example.com", "john", "123456", 5)
        );

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendEmail does nothing when JavaMailSender is absent (graceful fallback)")
    void testJavaMailSenderAbsent() {
        SmtpEmailService fallbackService = new SmtpEmailService(Optional.empty(), emailConfig);

        assertDoesNotThrow(() ->
                fallbackService.sendPasswordResetOtp("john@example.com", "john", "123456", 5)
        );
    }
}
