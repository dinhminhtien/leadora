package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationAcceptanceLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationAcceptanceLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UC-14.x — Customer rejects the quotation via the public portal.
 * Guarded by token presence, validity, and status of the quotation (must be PENDING_CUSTOMER_RESPONSE).
 * Transitions quotation status to {@code REJECTED}, records the decision/reason, and notifies the Sales representative.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerRejectQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationConfirmationTokenRepository tokenRepository;
    private final QuotationAcceptanceLogRepository acceptanceLogRepository;
    private final NotificationRepository notificationRepository;
    private final GetQuotationByTokenUseCase getQuotationByTokenUseCase;
    private final ActivityLogPublisher activityLogPublisher;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public QuotationEntity execute(UUID quotationId, String token, String reason, String ipAddress, String userAgent) {
        log.info("Processing customer rejection for quotation: {}, IP: {}", quotationId, ipAddress);

        // 1. Validate secure link token
        getQuotationByTokenUseCase.validateToken(quotationId, token);

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found", HttpStatus.NOT_FOUND));

        String previousStatus = quotation.getStatus().name();

        // Guard: must be in PENDING_CUSTOMER_RESPONSE status
        if (quotation.getStatus() != QuotationStatus.PENDING_CUSTOMER_RESPONSE) {
            throw new BusinessException("INVALID_QUOTATION_STATUS",
                    "Quotation cannot be rejected in its current status: " + quotation.getStatus(), HttpStatus.BAD_REQUEST);
        }

        if (reason == null || reason.trim().isBlank()) {
            throw new BusinessException("REASON_MANDATORY", "Rejection reason is mandatory.", HttpStatus.BAD_REQUEST);
        }

        // 2. Transition status to REJECTED
        quotation.setStatus(QuotationStatus.REJECTED);
        quotation = quotationRepository.save(quotation);

        // 3. Mark secure token as used
        QuotationConfirmationTokenEntity tokenEntity = tokenRepository.findByQuotationId(quotationId).orElse(null);
        if (tokenEntity != null && tokenEntity.getUsedAt() == null) {
            tokenEntity.setUsedAt(OffsetDateTime.now());
            tokenRepository.save(tokenEntity);
        }

        // 4. Write rejection log (customerNote stores the reason)
        QuotationAcceptanceLogEntity logEntity = QuotationAcceptanceLogEntity.builder()
                .quotationId(quotationId)
                .action("REJECTED")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .customerNote(reason)
                .build();
        acceptanceLogRepository.save(logEntity);

        // 5. System audit log
        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "CUSTOMER_REJECTED_PORTAL",
                quotation.getCreatedBy(), previousStatus, QuotationStatus.REJECTED.name(),
                "Customer rejected quotation via portal. Reason: " + reason + ", IP: " + ipAddress);

        // 6. Activity log
        activityLogPublisher.publish(
                ActivityLogType.QUOTATION_UPDATED,
                EntityType.QUOTATION,
                quotation.getQuotationId(),
                "Quotation rejected by customer via portal. Reason: " + reason,
                null
        );

        // 7. Notify Sales Owner
        if (quotation.getCreatedBy() != null) {
            try {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(quotation.getCreatedBy())
                        .title("Quotation Rejected by Customer")
                        .message("Quotation QT-" + quotationId.toString().substring(0, 8).toUpperCase() +
                                 " was rejected by the customer. Reason: " + reason)
                        .type("CUSTOMER_REJECTION")
                        .relatedEntity("QUOTATION")
                        .relatedId(quotationId)
                        .build();
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.warn("Rejection notification failed for quotation {}: {}", quotationId, e.getMessage());
            }
        }

        return quotation;
    }
}
