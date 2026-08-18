package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.email.EmailContactPolicy;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationSendLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationSendLogRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.novax.leadora.config.QuotationProperties;
import com.novax.leadora.infrastructure.persistence.entity.QuotationConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationConfirmationTokenRepository;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationSendLogRepository sendLogRepository;
    private final QuotationEmailService quotationEmailService;
    private final QuotationActionPolicy quotationActionPolicy;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final SystemAuditLogService systemAuditLogService;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;
    private final QuotationConfirmationTokenRepository tokenRepository;
    private final QuotationProperties quotationProperties;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    private static final SecureRandom secureRandom = new SecureRandom();

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing not available", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return !isBlank(preferred) ? preferred.trim() : (isBlank(fallback) ? null : fallback.trim());
    }

    @Transactional
    public QuotationResponse execute(UUID quotationId, SendQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        // BR-21 (status) and the customer's existence, from the same policy the eligibility
        // endpoint reads — so the Send button is never live for a send this would refuse.
        quotationActionPolicy.canSend(quotation).assertAllowed();

        boolean isEmail = "EMAIL".equalsIgnoreCase(request.getSendMethod());
        boolean isPhone = "WHATSAPP".equalsIgnoreCase(request.getSendMethod());

        // E3: the address actually used. What the sender typed wins — a rep may legitimately
        // send to a different person at a corporate customer — and the customer record is the
        // fallback, so a client that sends no address still reaches the right inbox. When
        // neither exists the send is refused: substituting a placeholder would file the
        // quotation as delivered to a mailbox nobody reads.
        String recipientEmail = firstNonBlank(request.getRecipientEmail(), quotation.getCustomer().getEmail());
        String recipientPhone = firstNonBlank(request.getRecipientPhone(), quotation.getCustomer().getPhone());
        String recipientName = firstNonBlank(request.getRecipientName(), quotation.getCustomer().getFullName());

        if (isEmail) {
            recipientEmail = EmailContactPolicy.requireDeliverableEmail(recipientEmail,
                    isBlank(request.getRecipientEmail()) ? "customer.email" : "recipientEmail",
                    "this quotation");
        } else if (isPhone) {
            recipientPhone = EmailContactPolicy.requireContactPhone(recipientPhone,
                    isBlank(request.getRecipientPhone()) ? "customer.phone" : "recipientPhone",
                    "this quotation");
        }
        if (isBlank(recipientName)) {
            throw BusinessException.forField("INVALID_CONTACT_INFORMATION",
                    "A recipient name is required, and this customer has none recorded. "
                            + "Add the customer's name, or enter one for this send.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "customer.fullName");
        }

        UserEntity actor = currentUserProvider.resolve(null);
        String actorRole = actor.getRole() != null ? actor.getRole().getRoleName() : null;

        // Generate secure link token (64-char hex)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String tokenHex = bytesToHex(tokenBytes);
        String tokenHash = hashSha256(tokenHex);

        // Delete any existing token for this quotation to prevent leaks
        tokenRepository.deleteByQuotationId(quotation.getQuotationId());

        // Save token to DB (valid for 24 hours)
        QuotationConfirmationTokenEntity tokenEntity = QuotationConfirmationTokenEntity.builder()
                .quotationId(quotation.getQuotationId())
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(tokenEntity);

        String portalBase = quotationProperties.getPortalBaseUrl();
        if (portalBase == null || portalBase.isBlank()) {
            portalBase = frontendUrl;
        }
        if (portalBase != null && portalBase.endsWith("/")) {
            portalBase = portalBase.substring(0, portalBase.length() - 1);
        }
        String secureLink = portalBase + "/portal/quotations/" + quotation.getQuotationId() + "?token=" + tokenHex;

        // POST-3: send the email FIRST when method is EMAIL (UC-14.4 E4) — a delivery
        // failure must surface as an error, not silently leave the quotation marked
        // PENDING_CUSTOMER_RESPONSE when the customer never actually received it.
        if (isEmail) {
            quotationEmailService.sendQuotationEmail(quotation, recipientEmail, recipientName,
                    request.getPersonalMessage(), actor.getFullName(), secureLink);
        }

        // POST-1: Update status to PENDING_CUSTOMER_RESPONSE
        quotation.setStatus(QuotationStatus.PENDING_CUSTOMER_RESPONSE);
        quotation.setSentAt(OffsetDateTime.now());
        QuotationEntity saved = quotationRepository.save(quotation);

        // POST-2: Record send log (BR-37: actor, action, timestamp, recipient details).
        // The resolved recipient, not the request's — otherwise a send that fell back to the
        // customer record files as having gone to nobody, and Resend then has no address to reuse.
        QuotationSendLogEntity sendLog = QuotationSendLogEntity.builder()
                .quotation(saved)
                .version(saved.getVersion() != null ? saved.getVersion() : 1)
                .sendMethod(request.getSendMethod())
                .recipientName(recipientName)
                .recipientEmail(recipientEmail)
                .recipientPhone(recipientPhone)
                .sentByName(actor.getFullName())
                .sentByRole(actorRole)
                .personalMessage(request.getPersonalMessage())
                .build();
        sendLogRepository.save(sendLog);

        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "SENT", actor,
                "APPROVED", "PENDING_CUSTOMER_RESPONSE", "via " + request.getSendMethod() + " to " + recipientName);

        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("sendMethod", request.getSendMethod())
                    .put("recipientName", recipientName)
                    .put("recipientEmail", recipientEmail)
                    .put("previousStatus", "APPROVED")
                    .put("newStatus", "PENDING_CUSTOMER_RESPONSE");
            activityLogPublisher.publish(
                    ActivityLogType.QUOTATION_UPDATED,
                    EntityType.QUOTATION,
                    saved.getQuotationId(),
                    "Quotation sent to customer (pending customer response)",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish quotation sent activity: {}", e.getMessage());
        }

        // UC-17.2: auto-resolve SLA tracking — quotation sent = action completed
        try {
            resolveSlaBreachUseCase.executeByEntity("QUOTATION", quotationId);
        } catch (Exception e) {
            log.warn("SLA auto-resolve failed for quotation {}: {}", quotationId, e.getMessage());
        }

        // UC-15.1: notify the quotation owner that it was dispatched to the customer
        if (saved.getCreatedBy() != null) {
            try {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(saved.getCreatedBy())
                        .title("Quotation Sent")
                        .message("Your quotation was sent to " + recipientName + " via " + request.getSendMethod() + ".")
                        .type("QUOTATION_SENT")
                        .relatedEntity("QUOTATION")
                        .relatedId(quotationId)
                        .build();
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.warn("Quotation-sent notification failed for quotation {}: {}", quotationId, e.getMessage());
            }
        }

        return QuotationResponse.from(saved);
    }
}
