package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ProcessApprovalRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationApprovalHistoryEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationApprovalHistoryRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessQuotationApprovalUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationApprovalHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService systemAuditLogService;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuotationResponse execute(UUID quotationId, ProcessApprovalRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        // E3: Quotation Already Processed
        if (quotation.getStatus() != QuotationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Quotation is no longer pending approval. Current status: " + quotation.getStatus().name());
        }

        // Manager identity is resolved server-side (BR-37) — the controller already
        // restricts this endpoint to hasRole('MANAGER').
        UserEntity manager = currentUserProvider.resolve(null);
        String managerRole = manager.getRole() != null ? manager.getRole().getRoleName() : null;

        QuotationStatus previousStatus = quotation.getStatus();
        QuotationStatus newStatus;
        String historyAction;

        switch (request.getAction().toUpperCase()) {
            case "APPROVE":
                newStatus = QuotationStatus.APPROVED;
                historyAction = "APPROVED";
                quotation.setApprovedAt(OffsetDateTime.now());
                quotation.setApprovedBy(manager);
                break;
            case "REJECT":
                newStatus = QuotationStatus.REJECTED;
                historyAction = "REJECTED";
                break;
            case "REQUEST_CHANGES":
                newStatus = QuotationStatus.PENDING_REVISION;
                historyAction = "REVISION_REQUESTED";
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid action: " + request.getAction() + ". Expected: APPROVE, REJECT, REQUEST_CHANGES");
        }

        quotation.setStatus(newStatus);
        // Optimistic lock (versionLock) is checked on this save — a concurrent approval
        // by another manager since findById() above throws OptimisticLockingFailureException (E3).
        QuotationEntity saved = quotationRepository.save(quotation);

        if (newStatus == QuotationStatus.APPROVED) {
            try {
                startSlaTrackingUseCase.execute("QUOTATION_SENT", "QUOTATION", saved.getQuotationId());
            } catch (Exception e) {
                log.warn("SLA tracking failed for quotation {}: {}", saved.getQuotationId(), e.getMessage());
            }
        }

        // BR-37: Record approval decision in audit history
        QuotationApprovalHistoryEntity historyEntry = QuotationApprovalHistoryEntity.builder()
                .quotation(saved)
                .action(historyAction)
                .decidedByName(manager.getFullName())
                .decidedByRole(managerRole)
                .notes(request.getNotes())
                .previousStatus(previousStatus.name())
                .newStatus(newStatus.name())
                .build();
        historyRepository.save(historyEntry);

        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, historyAction, manager,
                previousStatus.name(), newStatus.name(), request.getNotes());

        try {
            ActivityLogType logType;
            if (newStatus == QuotationStatus.APPROVED) {
                logType = ActivityLogType.QUOTATION_APPROVED;
            } else if (newStatus == QuotationStatus.REJECTED) {
                logType = ActivityLogType.QUOTATION_REJECTED;
            } else {
                logType = ActivityLogType.QUOTATION_UPDATED;
            }
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("action", historyAction)
                    .put("previousStatus", previousStatus.name())
                    .put("newStatus", newStatus.name());
            if (request.getNotes() != null) {
                payload.put("notes", request.getNotes());
            }
            activityLogPublisher.publish(
                    logType,
                    EntityType.QUOTATION,
                    saved.getQuotationId(),
                    "Quotation " + historyAction.toLowerCase().replace("_", " "),
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish quotation approval/rejection activity: {}", e.getMessage());
        }

        // BR-37: Notify the Sales Staff who created the quotation of the manager decision
        UserEntity creator = quotation.getCreatedBy();
        if (creator != null) {
            String notifTitle = "Quotation " + historyAction + ": " + quotationId.toString().substring(0, 8).toUpperCase();
            String notifMsg = String.format("Your quotation was %s by %s (%s).%s",
                    historyAction.toLowerCase().replace("_", " "),
                    manager.getFullName(),
                    managerRole,
                    request.getNotes() != null ? " Notes: " + request.getNotes() : "");
            NotificationEntity notification = NotificationEntity.builder()
                    .user(creator)
                    .title(notifTitle)
                    .message(notifMsg)
                    .type("QUOTATION_APPROVAL")
                    .relatedEntity("QUOTATION")
                    .relatedId(quotationId)
                    .build();
            notificationRepository.save(notification);
            log.info("Quotation approval notification sent to userId={}", creator.getUserId());
        }

        return QuotationResponse.from(saved);
    }
}
