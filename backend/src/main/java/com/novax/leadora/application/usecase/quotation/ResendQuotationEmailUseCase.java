package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationSendLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
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
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.novax.leadora.config.QuotationProperties;
import com.novax.leadora.infrastructure.persistence.entity.QuotationConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationConfirmationTokenRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendQuotationEmailUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationSendLogRepository sendLogRepository;
    private final QuotationEmailService quotationEmailService;
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

    @Transactional
    public QuotationResponse execute(UUID quotationId) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        // Resend is only allowed if it has been sent already and is awaiting response
        if (quotation.getStatus() != QuotationStatus.PENDING_CUSTOMER_RESPONSE) {
            throw new BusinessException("INVALID_STATUS_FOR_RESEND",
                    "Only quotations currently pending customer response can be resent.", HttpStatus.BAD_REQUEST);
        }

        // Retrieve current token
        QuotationConfirmationTokenEntity tokenEntity = tokenRepository.findByQuotationId(quotationId)
                .orElseThrow(() -> new BusinessException("TOKEN_NOT_FOUND",
                        "No active link session found for this quotation. Please send it first.", HttpStatus.BAD_REQUEST));

        // CRITICAL REQUIREMENT: "chỉ trong trường hợp email cũ chưa dc mở"
        if (tokenEntity.getOpenedAt() != null) {
            throw new BusinessException("LINK_ALREADY_OPENED",
                    "Cannot resend email because the customer has already opened/accessed the previous quotation link.", HttpStatus.BAD_REQUEST);
        }

        // Retrieve last send details
        List<QuotationSendLogEntity> sendLogs = sendLogRepository.findByQuotation_QuotationIdOrderByCreatedAtDesc(quotationId);
        if (sendLogs.isEmpty()) {
            throw new BusinessException("SEND_HISTORY_NOT_FOUND",
                    "No previous send log found for this quotation. Please send it first.", HttpStatus.BAD_REQUEST);
        }

        QuotationSendLogEntity lastLog = sendLogs.get(0);

        UserEntity actor = currentUserProvider.resolve(null);
        String actorRole = actor.getRole() != null ? actor.getRole().getRoleName() : null;

        // Generate new secure link token (invalidates/deletes old one)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String tokenHex = bytesToHex(tokenBytes);
        String tokenHash = hashSha256(tokenHex);

        // Delete the old token (locks/invalidates it)
        tokenRepository.deleteByQuotationId(quotation.getQuotationId());

        // Save new token to DB (valid for 24 hours)
        QuotationConfirmationTokenEntity newTokenEntity = QuotationConfirmationTokenEntity.builder()
                .quotationId(quotation.getQuotationId())
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(newTokenEntity);

        String portalBase = quotationProperties.getPortalBaseUrl();
        if (portalBase == null || portalBase.isBlank()) {
            portalBase = frontendUrl;
        }
        if (portalBase != null && portalBase.endsWith("/")) {
            portalBase = portalBase.substring(0, portalBase.length() - 1);
        }
        String secureLink = portalBase + "/portal/quotations/" + quotation.getQuotationId() + "?token=" + tokenHex;

        // The address the quotation actually went to last time, not the customer record as it
        // stands now: a resend replaces a link the same person is waiting on, so redirecting it
        // to a newer address would leave the original recipient holding a token that no longer
        // works and no explanation.
        if ("EMAIL".equalsIgnoreCase(lastLog.getSendMethod())) {
            quotationEmailService.sendQuotationEmail(quotation, lastLog.getRecipientEmail(),
                    lastLog.getRecipientName(), lastLog.getPersonalMessage(), actor.getFullName(), secureLink);
        }

        // Update send time
        quotation.setSentAt(OffsetDateTime.now());
        QuotationEntity saved = quotationRepository.save(quotation);

        // Record a new send log entry
        QuotationSendLogEntity sendLog = QuotationSendLogEntity.builder()
                .quotation(saved)
                .version(saved.getVersion() != null ? saved.getVersion() : 1)
                .sendMethod(lastLog.getSendMethod())
                .recipientName(lastLog.getRecipientName())
                .recipientEmail(lastLog.getRecipientEmail())
                .recipientPhone(lastLog.getRecipientPhone())
                .sentByName(actor.getFullName())
                .sentByRole(actorRole)
                .personalMessage(lastLog.getPersonalMessage())
                .build();
        sendLogRepository.save(sendLog);

        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "RESENT", actor,
                "PENDING_CUSTOMER_RESPONSE", "PENDING_CUSTOMER_RESPONSE", "resending email to " + lastLog.getRecipientName());

        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("recipientName", lastLog.getRecipientName())
                    .put("recipientEmail", lastLog.getRecipientEmail())
                    .put("action", "RESENT");
            activityLogPublisher.publish(
                    ActivityLogType.QUOTATION_UPDATED,
                    EntityType.QUOTATION,
                    saved.getQuotationId(),
                    "Quotation email resent to customer",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish quotation resent activity: {}", e.getMessage());
        }

        return QuotationResponse.from(saved);
    }
}
