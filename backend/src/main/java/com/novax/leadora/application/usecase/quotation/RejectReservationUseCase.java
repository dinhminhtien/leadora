package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.application.event.ReservationRejectedEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReservationDecisionLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReservationRejectReason;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReservationDecisionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BR-15 — Reject Reservation Request.
 * Invoked by Reservation staff to reject a portal-accepted quotation due to unavailability or other reasons.
 * Guards that the quotation status is in RESERVATION_PENDING.
 * Transition status to RESERVATION_REJECTED and notifies the Sales representative.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RejectReservationUseCase {

    private final QuotationRepository quotationRepository;
    private final ReservationDecisionLogRepository decisionLogRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ActivityLogPublisher activityLogPublisher;
    private final SystemAuditLogService systemAuditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public QuotationEntity execute(UUID quotationId, ReservationRejectReason reason, String note) {
        log.info("Reservation staff rejecting quotation: {}, reason: {}", quotationId, reason);

        if (reason == null) {
            throw new BusinessException("REJECT_REASON_MANDATORY", "Rejection reason is mandatory.", HttpStatus.BAD_REQUEST);
        }

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found", HttpStatus.NOT_FOUND));

        String previousStatus = quotation.getStatus().name();

        // Guard: must be in RESERVATION_PENDING status
        if (quotation.getStatus() != QuotationStatus.RESERVATION_PENDING) {
            throw new BusinessException("INVALID_QUOTATION_STATUS",
                    "Only quotations in RESERVATION_PENDING status can be rejected. Current: " + quotation.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // 1. Resolve current Reservation staff user
        UserEntity reservationStaff = currentUserProvider.resolve(null);
        UUID staffId = reservationStaff != null ? reservationStaff.getUserId() : null;

        // 2. Transition status to RESERVATION_REJECTED
        quotation.setStatus(QuotationStatus.RESERVATION_REJECTED);
        quotation = quotationRepository.save(quotation);

        // 3. Log reservation decision
        ReservationDecisionLogEntity decisionLog = ReservationDecisionLogEntity.builder()
                .quotationId(quotationId)
                .decision("REJECTED")
                .rejectReason(reason)
                .note(note)
                .decidedBy(staffId)
                .bookingId(null)
                .build();
        decisionLogRepository.save(decisionLog);

        // 4. System audit log
        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "RESERVATION_REJECTED",
                reservationStaff, previousStatus, QuotationStatus.RESERVATION_REJECTED.name(),
                "Reservation rejected. Reason: " + reason + ". Note: " + note);

        // 5. Activity log
        activityLogPublisher.publish(
                ActivityLogType.QUOTATION_UPDATED,
                EntityType.QUOTATION,
                quotation.getQuotationId(),
                "Reservation request rejected by Reservation staff. Reason: " + reason,
                null
        );

        // 6. Notify Sales Owner
        if (quotation.getCreatedBy() != null) {
            try {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(quotation.getCreatedBy())
                        .title("Reservation Rejected for Quotation")
                        .message("Reservation was rejected for quotation QT-" +
                                 quotationId.toString().substring(0, 8).toUpperCase() +
                                 ". Reason: " + reason + ". Note: " + (note != null ? note : "None"))
                        .type("RESERVATION_REJECTION")
                        .relatedEntity("QUOTATION")
                        .relatedId(quotationId)
                        .build();
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.warn("Rejection notification failed for quotation {}: {}", quotationId, e.getMessage());
            }
        }

        // 7. Publish Event
        eventPublisher.publishEvent(new ReservationRejectedEvent(quotation, reason, note));

        log.info("Quotation {} successfully rejected by Reservation staff.", quotationId);
        return quotation;
    }
}
