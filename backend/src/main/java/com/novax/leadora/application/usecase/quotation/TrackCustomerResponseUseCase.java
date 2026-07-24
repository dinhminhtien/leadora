package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.TrackCustomerResponseRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationCustomerResponseEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationCustomerResponseRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackCustomerResponseUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationCustomerResponseRepository customerResponseRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public QuotationResponse execute(UUID quotationId, TrackCustomerResponseRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        // PRE-1: Response can only be recorded on SENT or INTERESTED quotations
        if (quotation.getStatus() != QuotationStatus.SENT
                && quotation.getStatus() != QuotationStatus.INTERESTED) {
            throw new IllegalStateException(
                    "Customer response can only be recorded for SENT quotations. Current status: "
                            + quotation.getStatus().name());
        }

        // E3: validate customerResponse value
        QuotationStatus newStatus = switch (request.getCustomerResponse().toUpperCase()) {
            case "ACCEPTED"     -> QuotationStatus.ACCEPTED;
            case "REJECTED"     -> QuotationStatus.REJECTED;
            case "INTERESTED"   -> QuotationStatus.INTERESTED;
            case "NEED_REVISION" -> QuotationStatus.PENDING_REVISION;
            default -> throw new IllegalArgumentException(
                    "Invalid customer response: " + request.getCustomerResponse()
                            + ". Must be one of: ACCEPTED, REJECTED, INTERESTED, NEED_REVISION");
        };

        // BR-37/POST-4: a lost reason is mandatory when the customer rejects the quotation
        if (newStatus == QuotationStatus.REJECTED
                && (request.getLostReason() == null || request.getLostReason().isBlank())) {
            throw new BusinessException("LOST_REASON_REQUIRED",
                    "A lost reason is required when recording a Rejected response", HttpStatus.BAD_REQUEST);
        }

        String previousStatus = quotation.getStatus().name();

        // POST-1: Update quotation status
        quotation.setStatus(newStatus);
        QuotationEntity saved = quotationRepository.save(quotation);

        UserEntity actor = currentUserProvider.resolve(null);
        String actorRole = actor.getRole() != null ? actor.getRole().getRoleName() : null;

        // POST-2 + BR-37: Log customer response for audit
        QuotationCustomerResponseEntity responseLog = QuotationCustomerResponseEntity.builder()
                .quotation(saved)
                .customerResponse(request.getCustomerResponse().toUpperCase())
                .lostReason(request.getLostReason())
                .notes(request.getNotes())
                .recordedByName(actor.getFullName())
                .recordedByRole(actorRole)
                .previousStatus(previousStatus)
                .newStatus(newStatus.name())
                .build();
        customerResponseRepository.save(responseLog);

        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "CUSTOMER_RESPONSE", actor,
                previousStatus, newStatus.name(), request.getCustomerResponse());

        // UC-15.1: notify the quotation owner of the customer's decision
        if (saved.getCreatedBy() != null) {
            try {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(saved.getCreatedBy())
                        .title("Customer Responded")
                        .message("Customer response recorded: " + request.getCustomerResponse().toUpperCase() + " for your quotation.")
                        .type("CUSTOMER_RESPONSE")
                        .relatedEntity("QUOTATION")
                        .relatedId(quotationId)
                        .build();
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.warn("Customer-response notification failed for quotation {}: {}", quotationId, e.getMessage());
            }
        }

        return QuotationResponse.from(saved);
    }
}
