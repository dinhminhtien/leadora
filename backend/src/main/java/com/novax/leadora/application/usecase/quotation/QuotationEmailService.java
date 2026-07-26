package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Slf4j
@Service
public class QuotationEmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@leadora.com}")
    private String fromAddress;

    public void sendQuotationEmail(QuotationEntity quotation, SendQuotationRequest request, String senderName) {
        if (mailSender == null) {
            log.warn("JavaMailSender not configured (set MAIL_HOST/USERNAME/PASSWORD in .env) — email skipped for quotation {}",
                    quotation.getQuotationId());
            return;
        }
        String recipientEmail = request.getRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No recipient email provided for quotation {} — skipping email send", quotation.getQuotationId());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            String quoteNo = "QT-" + quotation.getQuotationId().toString().substring(0, 8).toUpperCase();
            helper.setSubject("Room Quotation " + quoteNo + " — Leadora Hotel");
            helper.setText(buildEmailBody(quotation, quoteNo, request, senderName), true);
            mailSender.send(message);
            log.info("Quotation email sent: {} → {}", quoteNo, recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send quotation email for {}: {}", quotation.getQuotationId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send quotation email: " + e.getMessage(), e);
        }
    }

    private String buildEmailBody(QuotationEntity q, String quoteNo, SendQuotationRequest req, String senderNameOverride) {
        String customerName = (q.getCustomer() != null && q.getCustomer().getFullName() != null)
                ? q.getCustomer().getFullName() : req.getRecipientName();
        String roomType   = q.getRoomType()     != null ? escapeHtml(q.getRoomType())     : "—";
        String checkIn    = q.getCheckInDate()  != null ? q.getCheckInDate().toString()   : "—";
        String checkOut   = q.getCheckOutDate() != null ? q.getCheckOutDate().toString()  : "—";
        String validUntil = q.getValidUntil()   != null ? q.getValidUntil().toString()    : "—";
        String total      = formatCurrency(q.getTotalAmount());
        String policy     = q.getPaymentPolicy() != null ? escapeHtml(q.getPaymentPolicy()) : "—";
        String personalMsg = (req.getPersonalMessage() != null && !req.getPersonalMessage().isBlank())
                ? "<p style=\"color:#374151;margin:16px 0;font-size:14px;font-style:italic;border-left:3px solid #3b82f6;padding-left:12px;\">"
                  + escapeHtml(req.getPersonalMessage()) + "</p>"
                : "";
        String senderName = senderNameOverride != null ? escapeHtml(senderNameOverride) : "Leadora Sales Team";

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f4f6;padding:32px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:8px;overflow:hidden;\">"
                // Header
                + "<tr><td style=\"background:#1e3a5f;padding:28px 32px;\">"
                + "<p style=\"margin:0;color:#93c5fd;font-size:11px;letter-spacing:2px;text-transform:uppercase;\">Leadora Hotel CRM</p>"
                + "<h1 style=\"margin:6px 0 0;color:#ffffff;font-size:22px;font-weight:700;\">Room Quotation</h1>"
                + "<p style=\"margin:4px 0 0;color:#93c5fd;font-size:13px;\">" + quoteNo + "</p>"
                + "</td></tr>"
                // Body
                + "<tr><td style=\"padding:32px;\">"
                + "<p style=\"color:#374151;font-size:15px;margin:0 0 8px;\">Dear <strong>" + escapeHtml(customerName) + "</strong>,</p>"
                + "<p style=\"color:#6b7280;font-size:13px;margin:0 0 24px;\">Thank you for your interest. Please find your customised room quotation below.</p>"
                + personalMsg
                // Details table
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid #e5e7eb;border-radius:6px;overflow:hidden;font-size:13px;\">"
                + "<tr style=\"background:#f9fafb;\"><td style=\"padding:10px 16px;color:#6b7280;width:45%;\">Room Type</td>"
                + "<td style=\"padding:10px 16px;color:#111827;font-weight:600;\">" + roomType + "</td></tr>"
                + "<tr><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Check-In</td>"
                + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">" + checkIn + "</td></tr>"
                + "<tr style=\"background:#f9fafb;\"><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Check-Out</td>"
                + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">" + checkOut + "</td></tr>"
                + "<tr><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Payment Policy</td>"
                + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">" + policy + "</td></tr>"
                + "<tr style=\"background:#f9fafb;\"><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Valid Until</td>"
                + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">" + validUntil + "</td></tr>"
                + "<tr style=\"background:#1e3a5f;\"><td style=\"padding:12px 16px;color:#bfdbfe;font-weight:600;\">Total Amount</td>"
                + "<td style=\"padding:12px 16px;color:#ffffff;font-weight:700;font-size:16px;\">" + total + "</td></tr>"
                + "</table>"
                + "<p style=\"color:#6b7280;font-size:12px;margin:20px 0 0;\">To accept, revise, or enquire about this quotation, please reply to this email or contact your sales representative.</p>"
                + "</td></tr>"
                // Footer
                + "<tr><td style=\"background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0;color:#9ca3af;font-size:11px;\">Sent by <strong style=\"color:#6b7280;\">"
                + senderName + "</strong> via Leadora Hotel CRM</p>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
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