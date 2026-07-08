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

    public void sendResetPasswordHtmlEmail(String toEmail, String webResetLink, String mobileResetLink) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            String htmlMsg = "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 550px; margin: auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">"
                    +
                    "  <div style=\"text-align: center; margin-bottom: 20px;\">" +
                    "    <h2 style=\"color: #1e293b; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">Leadora</h2>"
                    +
                    "    <p style=\"color: #64748b; font-size: 12px; margin: 4px 0 0 0;\">Smart Hotel Sales System</p>"
                    +
                    "  </div>" +
                    "  <div style=\"border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;\"></div>" +
                    "  <h3 style=\"color: #0f172a; font-size: 16px; font-weight: 700; margin-top: 0; text-align: center;\">Reset Your Password</h3>"
                    +
                    "  <p style=\"color: #334155; font-size: 13px; line-height: 1.6; margin-bottom: 24px; text-align: center;\">"
                    +
                    "    Hello,<br>" +
                    "    We received a request to reset your password. Please choose your platform below to set a new password:"
                    +
                    "  </p>" +
                    "  <div style=\"margin: 20px 0; padding: 15px; background-color: #f8fafc; border-radius: 12px; border: 1px solid #f1f5f9;\">"
                    +
                    "    <!-- Web Platform Section -->" +
                    "    <div style=\"margin-bottom: 20px; text-align: center;\">" +
                    "      <p style=\"margin: 0 0 8px 0; font-size: 13px; font-weight: 600; color: #475569;\">Option 1: Reset on Web Browser</p>"
                    +
                    "      <a href=\"" + webResetLink
                    + "\" style=\"display: inline-block; background-color: #0284c7; color: #ffffff; text-decoration: none; padding: 10px 20px; font-size: 12px; font-weight: 700; border-radius: 6px; box-shadow: 0 2px 4px rgba(2, 132, 199, 0.15);\">Reset via Website</a>"
                    +
                    "      <p style=\"color: #64748b; font-size: 11px; margin: 6px 0 0 0; word-break: break-all;\">" +
                    "        Or copy link: <a href=\"" + webResetLink
                    + "\" style=\"color: #0284c7; text-decoration: none;\">" + webResetLink + "</a>" +
                    "      </p>" +
                    "    </div>" +
                    "    <div style=\"border-bottom: 1px dashed #e2e8f0; margin: 15px 0;\"></div>" +
                    "    <!-- Mobile Platform Section -->" +
                    "    <div style=\"text-align: center;\">" +
                    "      <p style=\"margin: 0 0 8px 0; font-size: 13px; font-weight: 600; color: #475569;\">Option 2: Reset on Mobile App</p>"
                    +
                    "      <a href=\"" + mobileResetLink
                    + "\" style=\"display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 20px; font-size: 12px; font-weight: 700; border-radius: 6px; box-shadow: 0 2px 4px rgba(37, 99, 235, 0.15);\">Open Mobile App</a>"
                    +
                    "      <p style=\"color: #64748b; font-size: 11px; margin: 6px 0 0 0; word-break: break-all;\">" +
                    "        Or copy link: <a href=\"" + mobileResetLink
                    + "\" style=\"color: #2563eb; text-decoration: none;\">" + mobileResetLink + "</a>" +
                    "      </p>" +
                    "    </div>" +
                    "  </div>" +
                    "  <p style=\"color: #94a3b8; font-size: 11px; text-align: center; margin-top: 20px;\">" +
                    "    * These links are secure and will expire in 15 minutes. If you did not initiate this request, you can safely ignore this email."
                    +
                    "  </p>" +
                    "  <div style=\"border-top: 1px solid #f1f5f9; margin-top: 25px; padding-top: 15px; text-align: center;\">"
                    +
                    "    <p style=\"color: #94a3b8; font-size: 11px; margin: 0;\">&copy; 2026 Leadora NovaX. All rights reserved.</p>"
                    +
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

    public void sendFeedbackInvitationEmail(String toEmail, String customerName, String feedbackLink) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            String htmlMsg = "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 550px; margin: auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">"
                    + "  <div style=\"text-align: center; margin-bottom: 20px;\">"
                    + "    <h2 style=\"color: #1e293b; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">Leadora</h2>"
                    + "    <p style=\"color: #64748b; font-size: 12px; margin: 4px 0 0 0;\">Smart Hotel Sales System</p>"
                    + "  </div>"
                    + "  <div style=\"border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;\"></div>"
                    + "  <h3 style=\"color: #0f172a; font-size: 16px; font-weight: 700; margin-top: 0; text-align: center;\">Service Feedback Request</h3>"
                    + "  <p style=\"color: #334155; font-size: 13px; line-height: 1.6; margin-bottom: 24px;\">"
                    + "    Dear " + customerName + ",<br><br>"
                    + "    Thank you for choosing Leadora Resort. We sincerely appreciate your trust in our services. "
                    + "    We would be grateful if you could take a few moments to share your experience with the support provided by our Sales Staff. "
                    + "    Your feedback helps us continuously improve our service quality and deliver a better experience for all our guests."
                    + "  </p>"
                    + "  <div style=\"text-align: center; margin: 30px 0;\">"
                    + "    <a href=\"" + feedbackLink
                    + "\" style=\"display: inline-block; background-color: #0284c7; color: #ffffff; text-decoration: none; padding: 12px 28px; font-size: 13px; font-weight: 700; border-radius: 6px; box-shadow: 0 4px 6px rgba(2, 132, 199, 0.15);\">Leave Your Feedback</a>"
                    + "  </div>"
                    + "  <p style=\"color: #94a3b8; font-size: 11px; text-align: center; margin-top: 20px;\">"
                    + "    * This secure link can only be used once and will expire in 30 days."
                    + "  </p>"
                    + "  <div style=\"border-top: 1px solid #f1f5f9; margin-top: 25px; padding-top: 15px; text-align: center;\">"
                    + "    <p style=\"color: #94a3b8; font-size: 11px; margin: 0;\">&copy; 2026 Leadora NovaX. All rights reserved.</p>"
                    + "  </div>"
                    + "</div>";

            helper.setText(htmlMsg, true);
            helper.setTo(toEmail);
            helper.setSubject("We'd Love Your Feedback on Your Experience at Leadora Resort");
            helper.setFrom(fromEmail);

            mailSender.send(mimeMessage);
            log.info("Feedback invitation HTML email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send feedback invitation HTML email to {}", toEmail, e);
            throw new RuntimeException("Could not send email. Please try again later.", e);
        }
    }
}