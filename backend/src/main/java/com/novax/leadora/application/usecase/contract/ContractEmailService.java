package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.application.usecase.email.EmailAttachment;
import com.novax.leadora.application.usecase.email.EmailGateway;
import com.novax.leadora.application.usecase.email.EmailRequest;
import com.novax.leadora.application.usecase.email.EmailTemplateRenderer;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractEmailService {

    private final EmailGateway emailGateway;
    private final EmailTemplateRenderer templateRenderer;

    private static final String CONTRACT_LINK_TEMPLATE = 
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
        + "<body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;\">"
        + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f4f6;padding:32px 0;\">"
        + "<tr><td align=\"center\">"
        + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:8px;overflow:hidden;\">"
        // Header
        + "<tr><td style=\"background:#1e3a5f;padding:28px 32px;\">"
        + "<p style=\"margin:0;color:#93c5fd;font-size:11px;letter-spacing:2px;text-transform:uppercase;\">Leadora Hotel CRM</p>"
        + "<h1 style=\"margin:6px 0 0;color:#ffffff;font-size:22px;font-weight:700;\">Sales Contract Verification</h1>"
        + "<p style=\"margin:4px 0 0;color:#93c5fd;font-size:13px;\">${contractCode}</p>"
        + "</td></tr>"
        // Body
        + "<tr><td style=\"padding:32px;\">"
        + "<p style=\"color:#374151;font-size:15px;margin:0 0 16px;\">Dear Customer,</p>"
        + "<p style=\"color:#4b5563;font-size:14px;line-height:22px;margin:0 0 24px;\">"
        + "Your quotation has been accepted and a formal commercial sales contract is ready for your review and acknowledgment. "
        + "Please open the secure portal link below to review the contract terms and proceed with confirmation.</p>"
        // CTA Button
        + "<div style=\"text-align:center;margin:30px 0;\">"
        + "<a href=\"${secureLink}\" style=\"background:#1e3a5f;color:#ffffff;padding:12px 28px;text-decoration:none;font-weight:bold;border-radius:4px;display:inline-block;box-shadow:0 2px 4px rgba(0,0,0,0.1);\">Review & Sign Contract</a>"
        + "</div>"
        + "<p style=\"color:#6b7280;font-size:12px;margin:20px 0 0;line-height:18px;\">"
        + "A copy of the contract PDF is also attached to this email for your records. If the button above does not work, please copy and paste this URL into your browser:<br>"
        + "<span style=\"color:#1e3a5f;\">${secureLink}</span>"
        + "</p>"
        + "</td></tr>"
        // Footer
        + "<tr><td style=\"background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
        + "<p style=\"margin:0;color:#9ca3af;font-size:11px;\">Sent via Leadora Hotel CRM. This is a secure system notification.</p>"
        + "</td></tr>"
        + "</table></td></tr></table></body></html>";

    private static final String CONTRACT_OTP_TEMPLATE = 
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
        + "<body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;\">"
        + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f4f6;padding:32px 0;\">"
        + "<tr><td align=\"center\">"
        + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:8px;overflow:hidden;\">"
        // Header
        + "<tr><td style=\"background:#1e3a5f;padding:28px 32px;\">"
        + "<p style=\"margin:0;color:#93c5fd;font-size:11px;letter-spacing:2px;text-transform:uppercase;\">Leadora Hotel CRM</p>"
        + "<h1 style=\"margin:6px 0 0;color:#ffffff;font-size:22px;font-weight:700;\">Verification Code (OTP)</h1>"
        + "<p style=\"margin:4px 0 0;color:#93c5fd;font-size:13px;\">${contractCode}</p>"
        + "</td></tr>"
        // Body
        + "<tr><td style=\"padding:32px;\">"
        + "<p style=\"color:#374151;font-size:15px;margin:0 0 16px;\">Dear Customer,</p>"
        + "<p style=\"color:#4b5563;font-size:14px;line-height:22px;margin:0 0 24px;\">"
        + "To complete the verification and acknowledgment of your contract <strong>${contractCode}</strong>, please use the following 6-digit verification code:</p>"
        // OTP Block
        + "<div style=\"text-align:center;margin:30px 0;\">"
        + "<span style=\"background:#f3f4f6;color:#1e3a5f;font-size:32px;font-weight:bold;letter-spacing:6px;padding:12px 36px;border:1px dashed #cbd5e1;border-radius:6px;display:inline-block;\">${otpCode}</span>"
        + "</div>"
        + "<p style=\"color:#6b7280;font-size:12px;line-height:18px;margin:20px 0 0;\">"
        + "This code is valid for <strong>15 minutes</strong>. If you did not request this code, please ignore this email or contact customer support."
        + "</p>"
        + "</td></tr>"
        // Footer
        + "<tr><td style=\"background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;\">"
        + "<p style=\"margin:0;color:#9ca3af;font-size:11px;\">Sent via Leadora Hotel CRM. This is a secure system notification.</p>"
        + "</td></tr>"
        + "</table></td></tr></table></body></html>";

    public void sendSecureLinkEmail(ContractEntity contract, String secureLink, byte[] pdfBytes) {
        String recipientEmail = contract.getSentTo();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No recipient email provided for contract {} — skipping email send", contract.getContractCode());
            return;
        }

        try {
            String htmlContent = templateRenderer.render(CONTRACT_LINK_TEMPLATE, Map.of(
                    "contractCode", contract.getContractCode(),
                    "secureLink", secureLink
            ));

            EmailAttachment attachment = new EmailAttachment(
                    contract.getContractCode() + ".pdf",
                    () -> new ByteArrayInputStream(pdfBytes)
            );

            EmailRequest emailRequest = new EmailRequest(
                    null,
                    List.of(recipientEmail),
                    List.of(),
                    List.of(),
                    "Commercial Contract " + contract.getContractCode() + " — Action Required",
                    htmlContent,
                    List.of(attachment),
                    "contract-link-" + contract.getId()
            );

            emailGateway.send(emailRequest);
            log.info("Contract secure link email processed: {} → {}", contract.getContractCode(), recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send contract link email for {}: {}", contract.getContractCode(), e.getMessage(), e);
            throw new RuntimeException("Failed to send contract link email: " + e.getMessage(), e);
        }
    }

    public void sendOtpEmail(ContractEntity contract, String otpCode) {
        String recipientEmail = contract.getSentTo();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No recipient email provided for contract {} OTP — skipping email send", contract.getContractCode());
            return;
        }

        try {
            String htmlContent = templateRenderer.render(CONTRACT_OTP_TEMPLATE, Map.of(
                    "contractCode", contract.getContractCode(),
                    "otpCode", otpCode
            ));

            EmailRequest emailRequest = new EmailRequest(
                    null,
                    List.of(recipientEmail),
                    List.of(),
                    List.of(),
                    "Your Verification Code: " + otpCode + " — Contract " + contract.getContractCode(),
                    htmlContent,
                    List.of(),
                    "contract-otp-" + contract.getId() + "-" + System.currentTimeMillis() / 60000 // unique per min
            );

            emailGateway.send(emailRequest);
            log.info("Contract OTP email processed: {} → {}", contract.getContractCode(), recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send contract OTP email for {}: {}", contract.getContractCode(), e.getMessage(), e);
            throw new RuntimeException("Failed to send contract OTP email: " + e.getMessage(), e);
        }
    }
}
