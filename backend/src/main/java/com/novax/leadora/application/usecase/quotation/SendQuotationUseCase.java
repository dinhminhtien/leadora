package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationSendLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationSendLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public QuotationResponse execute(UUID quotationId, SendQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new RuntimeException("Quotation not found: " + quotationId));

        // BR-21: Only APPROVED quotations can be sent to customer
        if (quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only APPROVED quotations can be sent. Current status: " + quotation.getStatus().name());
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
                .sentByName(request.getSentByName())
                .sentByRole(request.getSentByRole())
                .personalMessage(request.getPersonalMessage())
                .build();
        sendLogRepository.save(sendLog);

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

        // POST-3: Send email when method is EMAIL (UC-14.4) — failure is logged but does not rollback DB state
        if ("EMAIL".equalsIgnoreCase(request.getSendMethod())) {
            try {
                quotationEmailService.sendQuotationEmail(saved, request);
            } catch (Exception e) {
                log.warn("Email delivery failed for quotation {} — status remains SENT: {}", quotationId, e.getMessage());
            }
        }

        return QuotationResponse.from(saved);
    }
}
