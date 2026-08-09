package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.application.usecase.email.EmailGateway;
import com.novax.leadora.application.usecase.email.EmailRequest;
import com.novax.leadora.application.usecase.email.EmailTemplateRenderer;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationEmailService {

    private final EmailGateway emailGateway;
    private final EmailTemplateRenderer templateRenderer;

    @Value("${app.frontend-url}")
    private String frontendUrl;


    private static final String QUOTATION_EMAIL_TEMPLATE = 
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
        + "<body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;\">"
        + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f4f6;padding:32px 0;\">"
        + "<tr><td align=\"center\">"
        + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:8px;overflow:hidden;\">"
        // Header
        + "<tr><td style=\"background:#1e3a5f;padding:28px 32px;\">"
        + "<p style=\"margin:0;color:#93c5fd;font-size:11px;letter-spacing:2px;text-transform:uppercase;\">Leadora Hotel CRM</p>"
        + "<h1 style=\"margin:6px 0 0;color:#ffffff;font-size:22px;font-weight:700;\">Room Quotation</h1>"
        + "<p style=\"margin:4px 0 0;color:#93c5fd;font-size:13px;\">${quoteNo}</p>"
        + "</td></tr>"
        // Body
        + "<tr><td style=\"padding:32px;\">"
        + "<p style=\"color:#374151;font-size:15px;margin:0 0 8px;\">Dear <strong>${customerName}</strong>,</p>"
        + "<p style=\"color:#6b7280;font-size:13px;margin:0 0 24px;\">Thank you for your interest. Please find your customised room quotation below.</p>"
        + "${personalMsg}"
        // Details table
        + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid #e5e7eb;border-radius:6px;overflow:hidden;font-size:13px;\">"
        + "<tr style=\"background:#f9fafb;\"><td style=\"padding:10px 16px;color:#6b7280;width:45%;\">Room Type</td>"
        + "<td style=\"padding:10px 16px;color:#111827;font-weight:600;\">${roomType}</td></tr>"
        + "<tr><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Check-In</td>"
        + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">${checkIn}</td></tr>"
        + "<tr style=\"background:#f9fafb;\"><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Check-Out</td>"
        + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">${checkOut}</td></tr>"
        + "<tr><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Payment Policy</td>"
        + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">${policy}</td></tr>"
        + "<tr style=\"background:#f9fafb;\"><td style=\"padding:10px 16px;color:#6b7280;border-top:1px solid #e5e7eb;\">Valid Until</td>"
        + "<td style=\"padding:10px 16px;color:#111827;border-top:1px solid #e5e7eb;\">${validUntil}</td></tr>"
        + "<tr style=\"background:#1e3a5f;\"><td style=\"padding:12px 16px;color:#bfdbfe;font-weight:600;\">Total Amount</td>"
        + "<td style=\"padding:12px 16px;color:#ffffff;font-weight:700;font-size:16px;\">${total}</td></tr>"
        + "</table>"
        + "<div style=\"margin:24px 0;text-align:center;\">"
        + "<a href=\"${portalLink}\" style=\"display:inline-block;background-color:#0284c7;color:#ffffff;text-decoration:none;padding:12px 28px;font-size:14px;font-weight:700;border-radius:6px;box-shadow:0 4px 6px rgba(2,132,199,0.15);\">Review & Accept Quotation</a>"
        + "</div>"
        + "<p style=\"color:#6b7280;font-size:11px;margin:20px 0 0;text-align:center;\">This secure portal link will expire in 7 days or after being accepted/rejected.</p>"
        + "</td></tr>"

        // Footer
        + "<tr><td style=\"background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
        + "<p style=\"margin:0;color:#9ca3af;font-size:11px;\">Sent by <strong style=\"color:#6b7280;\">${senderName}</strong> via Leadora Hotel CRM</p>"
        + "</td></tr>"
        + "</table></td></tr></table></body></html>";

    public void sendQuotationEmail(QuotationEntity quotation, SendQuotationRequest request, String senderName) {
        String recipientEmail = request.getRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No recipient email provided for quotation {} — skipping email send", quotation.getQuotationId());
            return;
        }

        try {
            String quoteNo = "QT-" + quotation.getQuotationId().toString().substring(0, 8).toUpperCase();
            String htmlContent = buildEmailBody(quotation, quoteNo, request, senderName);

            EmailRequest emailRequest = new EmailRequest(
                    null,
                    List.of(recipientEmail),
                    List.of(),
                    List.of(),
                    "Room Quotation " + quoteNo + " — Leadora Hotel",
                    htmlContent,
                    List.of(),
                    quotation.getQuotationId() != null ? "quotation-" + quotation.getQuotationId() : null
            );

            emailGateway.send(emailRequest);
            log.info("Quotation email successfully processed: {} → {}", quoteNo, recipientEmail);
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
        String portalLink = (q.getAcceptanceToken() != null)
                ? (frontendUrl + "/public/quotations/" + q.getAcceptanceToken())
                : "#";

        return templateRenderer.render(QUOTATION_EMAIL_TEMPLATE, Map.ofEntries(
                Map.entry("quoteNo", quoteNo),
                Map.entry("customerName", escapeHtml(customerName)),
                Map.entry("personalMsg", personalMsg),
                Map.entry("roomType", roomType),
                Map.entry("checkIn", checkIn),
                Map.entry("checkOut", checkOut),
                Map.entry("policy", policy),
                Map.entry("validUntil", validUntil),
                Map.entry("total", total),
                Map.entry("senderName", senderName),
                Map.entry("portalLink", portalLink)
        ));

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