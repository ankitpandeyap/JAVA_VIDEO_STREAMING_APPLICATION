package com.robspecs.streaming.serviceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.robspecs.streaming.exceptions.EmailSendingException;
import com.robspecs.streaming.service.MailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class MailSerivceImpl implements MailService {

    private final JavaMailSender mailSender;

    private static final Logger logger = LoggerFactory.getLogger(MailSerivceImpl.class);
    @Value("${app.frontend.password-reset-url}")
    private String frontendPasswordResetBaseUrl;



    @Autowired
    public MailSerivceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
         // Make sure it's assigned
        logger.debug("MailSerivceImpl initialized");
    }
    @Override
    @Async
    public void sendOtpEmail(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your OTP Code");

            String htmlContent = "<html><body>"
                    + "<div style='font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;'>"
                    + "<h2 style='color: #007bff; text-align: center;'>Your One-Time Password</h2>" // Improved heading
                    + "<p style='font-size: 16px; line-height: 1.6; text-align: center;'>Please use the following OTP to complete your registration:</p>"
                    + "<div style='background-color: #e9ecef; padding: 20px; text-align: center; border-radius: 10px; margin-bottom: 20px;'>" // Styled OTP box
                    + "<strong style='font-size: 24px; color: #212529;'>" + otp + "</strong>" // Larger, stronger OTP
                    + "</div>"
                    + "<p style='font-size: 14px; color: #6c757d; text-align: center;'>This OTP is valid for 5 minutes.</p>" // Expiry notice
                    + "<p style='font-size: 14px; color: #6c757d; text-align: center;'>If you did not request this OTP, please ignore this email.</p>"
                    + "</div>"
                    + "</body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", email);

        } catch (MessagingException e) {
            logger.error("Error sending OTP email to {}: {}", email, e.getMessage(), e); // Added 'e' to log stack trace
            // Changed to EmailSendingException
            throw new EmailSendingException("Failed to send OTP email", e);
        }
    }



    @Override // <--- ADD THIS METHOD
    public void sendPasswordResetEmail(String email, String token) {
        // Construct the full reset URL using the base URL from properties
        String resetUrl = frontendPasswordResetBaseUrl + "?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Password Reset Request");

            String htmlContent = "<html><body>"
                    + "<div style='font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;'>"
                    + "<h2 style='color: #dc3545; text-align: center;'>Password Reset Request</h2>"
                    + "<p style='font-size: 16px; line-height: 1.6;'>You have requested to reset your password. Please click the link below to set a new password:</p>"
                    + "<div style='text-align: center; margin: 20px 0;'>"
                    + "<a href=\"" + resetUrl + "\" style='display: inline-block; padding: 12px 25px; background-color: #007bff; color: white; text-decoration: none; border-radius: 5px; font-size: 16px;'>"
                    + "Reset Your Password"
                    + "</a>"
                    + "</div>"
                    + "<p style='font-size: 14px; color: #6c757d;'>This link is valid for " + PasswordResetTokenServiceImpl.TOKEN_EXPIRATION_MINUTES + " minutes. If you did not request a password reset, please ignore this email.</p>"
                    + "</div>"
                    + "</body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Password reset email sent successfully to: {}", email);

        } catch (MessagingException e) {
            logger.error("Error sending password reset email to {}: {}", email, e.getMessage(), e);
            throw new EmailSendingException("Failed to send password reset email", e);
        }
    }

    @Override
    @Async
    public void sendPasswordChangeConfirmationEmail(String toEmail) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

        try {
            helper.setTo(toEmail);
            helper.setSubject("Your Password Has Been Changed Successfully");
            helper.setFrom("no-reply@yourdomain.com"); // IMPORTANT: Replace with your actual sender email


            String htmlContent = "<html><body>"
                    + "<div style='font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;'>"
                    + "<h2 style='background-color: #28a745; color: white; padding: 10px; text-align: center; border-radius: 8px 8px 0 0;'>Password Changed Successfully!</h2>"
                    + "<div style='padding: 20px; line-height: 1.6;'>"
                    + "<p>Dear " + toEmail + ",</p>" // Dynamically insert email
                    + "<p>This is to confirm that your password for your account has been successfully changed.</p>"
                    + "<p>If you did not make this change, please contact our support team immediately.</p>"
                    + "<p>Thank you,</p>"
                    + "<p>The Crypto-Mail-APP Team</p>"
                    + "</div>"
                    + "<div style='text-align: center; padding: 20px; font-size: 0.8em; color: #777; border-top: 1px solid #eee; margin-top: 20px;'>"
                    + "<p>&copy; " + java.time.Year.now().getValue() + " Crypto-Mail-APP. All rights reserved.</p>"
                    + "<p>This is an automated email, please do not reply.</p>"
                    + "</div>"
                    + "</div>"
                    + "</body></html>";


            helper.setText(htmlContent, true); // true indicates HTML content

            mailSender.send(mimeMessage);
            logger.info("Password change confirmation email sent to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Error creating or sending password change confirmation email to {}: {}", toEmail, e.getMessage(), e);
            throw new EmailSendingException("Failed to send password change confirmation email", e);
        } catch (MailException e) {
            logger.error("Error sending password change confirmation email via MailSender to {}: {}", toEmail, e.getMessage(), e);
            throw new EmailSendingException("Failed to send password change confirmation email", e);
        }
    }
}
