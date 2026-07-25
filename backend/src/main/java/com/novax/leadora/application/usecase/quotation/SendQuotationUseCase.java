package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationSendLogRepository sendLogRepository;
    private final QuotationEmailService quotationEmailService;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public QuotationResponse execute(UUID quotationId, SendQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        // BR-21: Only APPROVED quotations can be sent to customer
        if (quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only APPROVED quotations can be sent. Current status: " + quotation.getStatus().name());
        }

        // E3: EMAIL method requires a valid recipient address
        boolean isEmail = "EMAIL".equalsIgnoreCase(request.getSendMethod());
        if (isEmail && (request.getRecipientEmail() == null || request.getRecipientEmail().isBlank())) {
            throw new BusinessException("INVALID_RECIPIENT_CONTACT",
                    "Recipient email is required when sending by EMAIL", HttpStatus.BAD_REQUEST);
        }

        UserEntity actor = currentUserProvider.resolve(null);
        String actorRole = actor.getRole() != null ? actor.getRole().getRoleName() : null;

        // POST-3: send the email FIRST when method is EMAIL (UC-14.4 E4) — a delivery
        // failure must surface as an error, not silently leave the quotation marked
        // SENT when the customer never actually received it.
        if (isEmail) {
            quotationEmailService.sendQuotationEmail(quotation, request, actor.getFullName());
        }

        // POST-1: Update status to SENT
        quotation.setStatus(QuotationStatus.SENT);
        quotation.setSentAt(OffsetDateTime.now());
        QuotationEntity saved = quotationRepository.save(quotation);

        // POST-2: Record send log (BR-37: actor, action, timestamp, recipient details)
        QuotationSendLogEntity sendLog = QuotationSendLogEntity.builder()
                .quotation(saved)
                .version(saved.getVersion() != null ? saved.getVersion() : 1)
                .sendMethod(request.getSendMethod())
                .recipientName(request.getRecipientName())
                .recipientEmail(request.getRecipientEmail())
                .recipientPhone(request.getRecipientPhone())
                .sentByName(actor.getFullName())
                .sentByRole(actorRole)
                .personalMessage(request.getPersonalMessage())
                .build();
        sendLogRepository.save(sendLog);

        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "SENT", actor,
                "APPROVED", "SENT", "via " + request.getSendMethod() + " to " + request.getRecipientName());

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
                        .message("Your quotation was sent to " + request.getRecipientName() + " via " + request.getSendMethod() + ".")
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
