package com.novax.leadora.application.usecase.email;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailGateway emailGateway;
    private final EmailTemplateRenderer templateRenderer;

    private static final String RESET_PASSWORD_TEMPLATE = 
        "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 550px; margin: auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">" +
        "  <div style=\"text-align: center; margin-bottom: 20px;\">" +
        "    <h2 style=\"color: #1e293b; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">Leadora</h2>" +
        "    <p style=\"color: #64748b; font-size: 12px; margin: 4px 0 0 0;\">Smart Hotel Sales System</p>" +
        "  </div>" +
        "  <div style=\"border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;\"></div>" +
        "  <h3 style=\"color: #0f172a; font-size: 16px; font-weight: 700; margin-top: 0; text-align: center;\">Reset Your Password</h3>" +
        "  <p style=\"color: #334155; font-size: 13px; line-height: 1.6; margin-bottom: 24px; text-align: center;\">" +
        "    Hello,<br>" +
        "    We received a request to reset your password. Please choose your platform below to set a new password:" +
        "  </p>" +
        "  <div style=\"margin: 20px 0; padding: 15px; background-color: #f8fafc; border-radius: 12px; border: 1px solid #f1f5f9;\">" +
        "    <!-- Web Platform Section -->" +
        "    <div style=\"margin-bottom: 20px; text-align: center;\">" +
        "      <p style=\"margin: 0 0 8px 0; font-size: 13px; font-weight: 600; color: #475569;\">Option 1: Reset on Web Browser</p>" +
        "      <a href=\"${webResetLink}\" style=\"display: inline-block; background-color: #0284c7; color: #ffffff; text-decoration: none; padding: 10px 20px; font-size: 12px; font-weight: 700; border-radius: 6px; box-shadow: 0 2px 4px rgba(2, 132, 199, 0.15);\">Reset via Website</a>" +
        "      <p style=\"color: #64748b; font-size: 11px; margin: 6px 0 0 0; word-break: break-all;\">" +
        "        Or copy link: <a href=\"${webResetLink}\" style=\"color: #0284c7; text-decoration: none;\">${webResetLink}</a>" +
        "      </p>" +
        "    </div>" +
        "    <div style=\"border-bottom: 1px dashed #e2e8f0; margin: 15px 0;\"></div>" +
        "    <!-- Mobile Platform Section -->" +
        "    <div style=\"text-align: center;\">" +
        "      <p style=\"margin: 0 0 8px 0; font-size: 13px; font-weight: 600; color: #475569;\">Option 2: Reset on Mobile App</p>" +
        "      <a href=\"${mobileResetLink}\" style=\"display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 20px; font-size: 12px; font-weight: 700; border-radius: 6px; box-shadow: 0 2px 4px rgba(37, 99, 235, 0.15);\">Open Mobile App</a>" +
        "      <p style=\"color: #64748b; font-size: 11px; margin: 6px 0 0 0; word-break: break-all;\">" +
        "        Or copy link: <a href=\"${mobileResetLink}\" style=\"color: #2563eb; text-decoration: none;\">${mobileResetLink}</a>" +
        "      </p>" +
        "    </div>" +
        "  </div>" +
        "  <p style=\"color: #94a3b8; font-size: 11px; text-align: center; margin-top: 20px;\">" +
        "    * These links are secure and will expire in 15 minutes. If you did not initiate this request, you can safely ignore this email." +
        "  </p>" +
        "  <div style=\"border-top: 1px solid #f1f5f9; margin-top: 25px; padding-top: 15px; text-align: center;\">" +
        "    <p style=\"color: #94a3b8; font-size: 11px; margin: 0;\">&copy; 2026 Leadora NovaX. All rights reserved.</p>" +
        "  </div>" +
        "</div>";

    private static final String FEEDBACK_INVITATION_TEMPLATE = 
        "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 550px; margin: auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">" +
        "  <div style=\"text-align: center; margin-bottom: 20px;\">" +
        "    <h2 style=\"color: #1e293b; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">Leadora</h2>" +
        "    <p style=\"color: #64748b; font-size: 12px; margin: 4px 0 0 0;\">Smart Hotel Sales System</p>" +
        "  </div>" +
        "  <div style=\"border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;\"></div>" +
        "  <h3 style=\"color: #0f172a; font-size: 16px; font-weight: 700; margin-top: 0; text-align: center;\">Service Feedback Request</h3>" +
        "  <p style=\"color: #334155; font-size: 13px; line-height: 1.6; margin-bottom: 24px;\">" +
        "    Dear ${customerName},<br><br>" +
        "    Thank you for choosing Leadora Resort. We sincerely appreciate your trust in our services. " +
        "    We would be grateful if you could take a few moments to share your experience with the support provided by our Sales Staff. " +
        "    Your feedback helps us continuously improve our service quality and deliver a better experience for all our guests." +
        "  </p>" +
        "  <div style=\"text-align: center; margin: 30px 0;\">" +
        "    <a href=\"${feedbackLink}\" style=\"display: inline-block; background-color: #0284c7; color: #ffffff; text-decoration: none; padding: 12px 28px; font-size: 13px; font-weight: 700; border-radius: 6px; box-shadow: 0 4px 6px rgba(2, 132, 199, 0.15);\">Leave Your Feedback</a>" +
        "  </div>" +
        "  <p style=\"color: #94a3b8; font-size: 11px; text-align: center; margin-top: 20px;\">" +
        "    * This secure link can only be used once and will expire in 30 days." +
        "  </p>" +
        "  <div style=\"border-top: 1px solid #f1f5f9; margin-top: 25px; padding-top: 15px; text-align: center;\">" +
        "    <p style=\"color: #94a3b8; font-size: 11px; margin: 0;\">&copy; 2026 Leadora NovaX. All rights reserved.</p>" +
        "  </div>" +
        "</div>";

    private static final String BOOKING_CONFIRMATION_TEMPLATE = 
        "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: auto; padding: 25px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;\">" +
        "  <div style=\"text-align: center; margin-bottom: 20px;\">" +
        "    <h2 style=\"color: #1e293b; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;\">Leadora</h2>" +
        "    <p style=\"color: #64748b; font-size: 12px; margin: 4px 0 0 0;\">Smart Hotel Sales System</p>" +
        "  </div>" +
        "  <div style=\"border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;\"></div>" +
        "  <h3 style=\"color: #16a34a; font-size: 18px; font-weight: 700; margin-top: 0; text-align: center;\">Booking Confirmed!</h3>" +
        "  <p style=\"color: #334155; font-size: 13px; line-height: 1.6; margin-bottom: 24px;\">" +
        "    Dear ${customerName},<br><br>" +
        "    We are pleased to inform you that your booking has been successfully confirmed. Below are the summary details of your reservation:" +
        "  </p>" +
        "  <div style=\"margin: 20px 0; padding: 15px; background-color: #f8fafc; border-radius: 12px; border: 1px solid #f1f5f9;\">" +
        "    <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size: 13px; line-height: 1.6; color: #475569;\">" +
        "      <tr><td style=\"font-weight: 600; width: 120px;\">Booking Code:</td><td style=\"color: #0f172a; font-weight: 700;\">${bookingCode}</td></tr>" +
        "      <tr><td style=\"font-weight: 600;\">Check-In Date:</td><td style=\"color: #0f172a;\">${checkIn}</td></tr>" +
        "      <tr><td style=\"font-weight: 600;\">Check-Out Date:</td><td style=\"color: #0f172a;\">${checkOut}</td></tr>" +
        "      <tr><td style=\"font-weight: 600; vertical-align: top;\">Special Requests:</td><td style=\"color: #0f172a;\">${specialRequests}</td></tr>" +
        "    </table>" +
        "  </div>" +
        "  <h4 style=\"color: #0f172a; font-size: 14px; font-weight: 700; margin: 20px 0 10px 0;\">Reservation Details</h4>" +
        "  <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse: collapse;\">" +
        "    <thead>" +
        "      <tr style=\"border-bottom: 2px solid #e2e8f0; text-align: left; font-size: 12px; color: #64748b;\">" +
        "        <th style=\"padding: 8px; font-weight: 600;\">Room/Service</th>" +
        "        <th style=\"padding: 8px; font-weight: 600; text-align: center;\">Qty</th>" +
        "        <th style=\"padding: 8px; font-weight: 600; text-align: center;\">Nights</th>" +
        "        <th style=\"padding: 8px; font-weight: 600; text-align: right;\">Rate</th>" +
        "        <th style=\"padding: 8px; font-weight: 600; text-align: right;\">Total</th>" +
        "      </tr>" +
        "    </thead>" +
        "    <tbody>" +
        "      ${itemsHtml}" +
        "    </tbody>" +
        "  </table>" +
        "  <div style=\"margin-top: 20px; padding: 15px; background-color: #f0fdf4; border-radius: 12px; border: 1px solid #bbf7d0; text-align: right;\">" +
        "    <span style=\"color: #166534; font-size: 13px; font-weight: 600;\">Total Amount: </span>" +
        "    <span style=\"color: #166534; font-size: 18px; font-weight: 800;\">${totalAmountStr}</span>" +
        "  </div>" +
        "  <p style=\"color: #64748b; font-size: 12px; margin-top: 20px; line-height: 1.6;\">" +
        "    Thank you for choosing Leadora Resort. If you have any questions or need to make changes to your booking, please do not hesitate to contact our customer support team." +
        "  </p>" +
        "  <div style=\"border-top: 1px solid #f1f5f9; margin-top: 25px; padding-top: 15px; text-align: center;\">" +
        "    <p style=\"color: #94a3b8; font-size: 11px; margin: 0;\">&copy; 2026 Leadora NovaX. All rights reserved.</p>" +
        "  </div>" +
        "</div>";

    public void sendResetPasswordHtmlEmail(String toEmail, String webResetLink, String mobileResetLink) {
        try {
            String htmlContent = templateRenderer.render(RESET_PASSWORD_TEMPLATE, Map.of(
                    "webResetLink", webResetLink,
                    "mobileResetLink", mobileResetLink
            ));

            EmailRequest emailRequest = new EmailRequest(
                    null,
                    List.of(toEmail),
                    List.of(),
                    List.of(),
                    "Reset Your Password - Leadora",
                    htmlContent,
                    List.of(),
                    null
            );

            emailGateway.send(emailRequest);
            log.info("Reset password email successfully processed for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to process reset password email for {}", toEmail, e);
            throw new RuntimeException("Could not send email. Please try again later.", e);
        }
    }

    public void sendFeedbackInvitationEmail(String toEmail, String customerName, String feedbackLink) {
        try {
            String htmlContent = templateRenderer.render(FEEDBACK_INVITATION_TEMPLATE, Map.of(
                    "customerName", customerName,
                    "feedbackLink", feedbackLink
            ));

            EmailRequest emailRequest = new EmailRequest(
                    null,
                    List.of(toEmail),
                    List.of(),
                    List.of(),
                    "We'd Love Your Feedback on Your Experience at Leadora Resort",
                    htmlContent,
                    List.of(),
                    null
            );

            emailGateway.send(emailRequest);
            log.info("Feedback invitation email successfully processed for {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to process feedback invitation email for {}", toEmail, e);
            throw new RuntimeException("Could not send email. Please try again later.", e);
        }
    }

    public void sendBookingConfirmationEmail(BookingEntity booking, List<BookingDetailEntity> details) {
        if (booking == null || booking.getCustomer() == null) {
            log.warn("Booking or Customer is null — skipping confirmation email");
            return;
        }
        String toEmail = booking.getCustomer().getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No customer email available for booking {} — skipping confirmation email", booking.getBookingCode());
            return;
        }

        try {
            String bookingCode = booking.getBookingCode();
            String customerName = booking.getCustomer().getFullName();
            String checkIn = booking.getCheckInDate() != null ? booking.getCheckInDate().toString() : "—";
            String checkOut = booking.getCheckOutDate() != null ? booking.getCheckOutDate().toString() : "—";
            String totalAmountStr = formatCurrency(booking.getTotalAmount());
            String specialRequests = booking.getSpecialRequests() != null ? escapeHtml(booking.getSpecialRequests()) : "—";

            StringBuilder itemsHtml = new StringBuilder();
            if (details != null && !details.isEmpty()) {
                for (BookingDetailEntity detail : details) {
                    String desc = detail.getDescription() != null ? escapeHtml(detail.getDescription()) : "—";
                    String roomNo = detail.getRoomNumber() != null ? escapeHtml(detail.getRoomNumber()) : "—";
                    String qty = String.valueOf(detail.getQuantity());
                    String nights = String.valueOf(detail.getNights());
                    String price = formatCurrency(detail.getUnitPrice());
                    String lineTotal = formatCurrency(detail.getLineTotal());

                    itemsHtml.append("<tr style=\"border-bottom: 1px solid #f1f5f9;\">")
                            .append("<td style=\"padding: 10px 8px; font-size: 13px; color: #334155;\">").append(desc).append("<br><small style=\"color: #64748b;\">Room: ").append(roomNo).append("</small></td>")
                            .append("<td style=\"padding: 10px 8px; font-size: 13px; color: #334155; text-align: center;\">").append(qty).append("</td>")
                            .append("<td style=\"padding: 10px 8px; font-size: 13px; color: #334155; text-align: center;\">").append(nights).append("</td>")
                            .append("<td style=\"padding: 10px 8px; font-size: 13px; color: #334155; text-align: right;\">").append(price).append("</td>")
                            .append("<td style=\"padding: 10px 8px; font-size: 13px; color: #0f172a; font-weight: 600; text-align: right;\">").append(lineTotal).append("</td>")
                            .append("</tr>");
                }
            } else {
                itemsHtml.append("<tr><td colspan=\"5\" style=\"padding: 15px; text-align: center; color: #64748b; font-size: 13px;\">No room details listed.</td></tr>");
            }

            String htmlContent = templateRenderer.render(BOOKING_CONFIRMATION_TEMPLATE, Map.of(
                    "customerName", escapeHtml(customerName),
                    "bookingCode", escapeHtml(bookingCode),
                    "checkIn", checkIn,
                    "checkOut", checkOut,
                    "specialRequests", specialRequests,
                    "itemsHtml", itemsHtml.toString(),
                    "totalAmountStr", totalAmountStr
            ));

            EmailRequest emailRequest = new EmailRequest(
                    null,
                    List.of(toEmail),
                    List.of(),
                    List.of(),
                    "Booking Confirmed: " + bookingCode + " — Leadora Hotel",
                    htmlContent,
                    List.of(),
                    booking.getBookingId() != null ? "booking-" + booking.getBookingId() : null
            );

            emailGateway.send(emailRequest);
            log.info("Booking confirmation email successfully processed for booking code {}", bookingCode);
        } catch (Exception e) {
            log.error("Failed to process booking confirmation email for booking {}", booking.getBookingCode(), e);
            throw new RuntimeException("Could not send booking confirmation email: " + e.getMessage(), e);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "—";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
