package com.novax.leadora.infrastructure.integration.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendResetPasswordHtmlEmail(String toEmail, String resetLink) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            String htmlMsg = "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 550px; margin: auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">" +
                    "  <div style=\"text-align: center; margin-bottom: 20px;\">" +
                    "    <h2 style=\"color: #1e293b; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">Leadora</h2>" +
                    "    <p style=\"color: #64748b; font-size: 12px; margin: 4px 0 0 0;\">Smart Hotel Sales CRM</p>" +
                    "  </div>" +
                    "  <div style=\"border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;\"></div>" +
                    "  <h3 style=\"color: #0f172a; font-size: 16px; font-weight: 700; margin-top: 0;\">Reset Your Password</h3>" +
                    "  <p style=\"color: #334155; font-size: 13px; line-height: 1.6; margin-bottom: 24px;\">" +
                    "    Hello,<br><br>" +
                    "    We received a request to reset the password for your Leadora CRM account. Click the button below to secure your account and set a new password:" +
                    "  </p>" +
                    "  <div style=\"text-align: center; margin: 25px 0;\">" +
                    "    <a href=\"" + resetLink + "\" style=\"display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 11px 24px; font-size: 13px; font-weight: 700; border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(37, 99, 235, 0.2);\">Reset Password</a>" +
                    "  </div>" +
                    "  <p style=\"color: #475569; font-size: 12px; line-height: 1.6;\">" +
                    "    Or copy and paste this link into your browser:<br>" +
                    "    <a href=\"" + resetLink + "\" style=\"color: #2563eb; word-break: break-all;\">" + resetLink + "</a>" +
                    "  </p>" +
                    "  <p style=\"color: #94a3b8; font-size: 11px; margin-top: 20px;\">" +
                    "    * This link is secure and will expire in 15 minutes. If you did not initiate this request, you can safely ignore this email." +
                    "  </p>" +
                    "  <div style=\"border-top: 1px solid #f1f5f9; margin-top: 25px; padding-top: 15px; text-align: center;\">" +
                    "    <p style=\"color: #94a3b8; font-size: 11px; margin: 0;\">&copy; 2026 Leadora Novax. All rights reserved.</p>" +
                    "  </div>" +
                    "</div>";

            helper.setText(htmlMsg, true);
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password - Leadora");
            helper.setFrom(fromEmail);

            mailSender.send(mimeMessage);
            log.info("Reset password HTML email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send reset password HTML email to {}", toEmail, e);
            throw new RuntimeException("Could not send email. Please try again later.", e);
        }
    }
}
