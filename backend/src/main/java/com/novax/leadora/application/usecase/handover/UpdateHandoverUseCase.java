package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.request.UpdateHandoverRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.timeline.CreateInteractionTimelineUseCase;
import com.novax.leadora.api.dto.request.CreateInteractionTimelineRequest;
import com.novax.leadora.application.usecase.handover.event.HandoverSubmittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UC-20.4 — Update Operational Handover Use Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHandoverUseCase {

    private final OpHandoverRepository opHandoverRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CreateInteractionTimelineUseCase createInteractionTimelineUseCase;
    private final HandoverAccessPolicy handoverAccessPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public ArrivalHandoverResponse execute(UUID handoverId, UpdateHandoverRequest request, UserEntity actor) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Operational handover", handoverId));

        // PRE-4 & E6-4.1: Unauthorized Update check.
        //
        // Delegated to the same policy the read path uses, for two reasons. The old inline check
        // was wrapped in `if (actor != null && actor.getRole() != null)`, so a caller with no role
        // skipped authorization altogether — it failed open, which is the one direction an
        // authorization check must never fail. And it allowed only ADMIN or the creator, so a
        // MANAGER was refused a record they are allowed to read, and a colleague could not touch a
        // handover on a booking assigned to them.
        if (actor == null) {
            throw new AccessDeniedException("Access Denied.");
        }
        handoverAccessPolicy.assertCanView(actor, handover);

        BookingEntity booking = handover.getBooking();

        // BR-26 & E6-3.2: Check Guest Arrival Time
        if (booking != null && LocalDate.now().isAfter(booking.getCheckInDate())) {
            throw new IllegalStateException("Cannot update past handovers.");
        }

        // BR-44: booking is closed / cancelled / rejected. Whitelist rather than blacklist so a
        // BookingStatus added later is treated as "not editable" until somebody decides otherwise
        // — the old list missed NO_SHOW and PENDING. Matches UPDATABLE_BOOKING_STATUSES on the
        // Front Office side.
        if (booking != null && !BookingStatus.LIVE_FOR_ARRIVAL.contains(booking.getStatus())) {
            throw new IllegalStateException(
                    "This booking is " + booking.getStatus()
                            + ", so its operational handover can no longer be updated.");
        }

        HandoverStatus newStatus;
        try {
            newStatus = HandoverStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid handover status: " + request.getStatus());
        }

        if (newStatus != HandoverStatus.DRAFT && newStatus != HandoverStatus.SUBMITTED) {
            throw new IllegalStateException("Only Draft or Submitted statuses are allowed.");
        }

        // Enforce validations from UC-20.4 specification when submitting
        if (newStatus == HandoverStatus.SUBMITTED) {
            // E6-2.2: Front Office Staff Not Selected
            if (request.getAssignedFoUserId() == null) {
                throw new IllegalStateException("Responsible Front Office Staff is required.");
            }

            // E6-1.2: Missing Required Handover Information
            boolean hasNotes = (request.getSpecialRequests() != null && !request.getSpecialRequests().trim().isEmpty()) ||
                               (request.getRoomPreferences() != null && !request.getRoomPreferences().trim().isEmpty()) ||
                               (request.getVipNotes() != null && !request.getVipNotes().trim().isEmpty()) ||
                               (request.getOperationalNotes() != null && !request.getOperationalNotes().trim().isEmpty());
            if (!hasNotes) {
                throw new IllegalStateException("Missing required handover information.");
            }
        }

        HandoverStatus oldStatus = handover.getStatus();

        // Update fields
        handover.setSpecialRequests(request.getSpecialRequests());
        handover.setRoomPreferences(request.getRoomPreferences());
        handover.setVipNotes(request.getVipNotes());
        handover.setOperationalNotes(request.getOperationalNotes());
        handover.setUpdatedBy(actor);
        handover.setAssignedFoUserId(request.getAssignedFoUserId());

        // If transitioning to SUBMITTED or re-submitting
        if (newStatus == HandoverStatus.SUBMITTED) {
            handover.setStatus(HandoverStatus.SUBMITTED);
            handover.setReadinessStatus(ReadinessStatus.PENDING_REVIEW);
            handover.setSubmittedAt(OffsetDateTime.now());
            handover.setClarificationNote(null); // Clear clarification note when re-submitting
        } else {
            handover.setStatus(HandoverStatus.DRAFT);
        }

        OpHandoverEntity saved = opHandoverRepository.save(handover);

        List<BookingDetailEntity> details = booking != null 
                ? bookingDetailRepository.findByBooking_BookingId(booking.getBookingId()) 
                : List.of();
        List<PaymentEntity> payments = booking != null 
                ? paymentRepository.findByBooking_BookingId(booking.getBookingId()) 
                : List.of();

        // BR-37 — a queryable audit row alongside the log line. The log file alone could not answer
        // "who changed this handover and from what", which is the whole point of the rule.
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("bookingCode", booking != null ? booking.getBookingCode() : null)
                    .put("previousStatus", oldStatus != null ? oldStatus.name() : null)
                    .put("newStatus", newStatus.name())
                    .put("assignedFoUserId", request.getAssignedFoUserId() != null
                            ? request.getAssignedFoUserId().toString() : null);
            activityLogPublisher.publish(
                    ActivityLogType.HANDOVER_SUBMITTED,
                    EntityType.HANDOVER,
                    saved.getHandoverId(),
                    "Operational handover " + oldStatus + " -> " + newStatus,
                    payload);
        } catch (Exception e) {
            log.warn("Failed to publish handover update activity: {}", e.getMessage());
        }

        log.info("[AUDIT] Action: UPDATE_OPERATIONAL_HANDOVER, TargetRecord: {}, OldStatus: {}, NewStatus: {}, UpdatedBy: {}, Timestamp: {}",
                saved.getHandoverId(), oldStatus, newStatus, actor != null ? actor.getUserId() : null, OffsetDateTime.now());

        if (newStatus == HandoverStatus.SUBMITTED && (oldStatus != HandoverStatus.SUBMITTED || oldStatus == HandoverStatus.DRAFT)) {
            // Update Interaction Timeline
            if (booking != null) {
                try {
                    CreateInteractionTimelineRequest timelineReq = new CreateInteractionTimelineRequest();
                    timelineReq.setType("HANDOVER_SUBMISSION");
                    timelineReq.setDescription("Operational handover updated and submitted for booking: " + booking.getBookingCode());
                    timelineReq.setOccurredAt(OffsetDateTime.now());
                    if (booking.getQuotation() != null && booking.getQuotation().getDeal() != null) {
                        timelineReq.setDealId(booking.getQuotation().getDeal().getDealId());
                    }
                    if (booking.getCustomer() != null) {
                        timelineReq.setCustomerId(booking.getCustomer().getCustomerId());
                    }
                    createInteractionTimelineUseCase.execute(timelineReq);
                } catch (Exception e) {
                    log.error("Failed to record Interaction Timeline: ", e);
                }
            }

            // Notify Front Office Staff (POST-4)
            sendNotificationToFrontOffice(saved, actor, request.getAssignedFoUserId());

            // Publish HandoverSubmittedEvent to send decoupled emails
            try {
                eventPublisher.publishEvent(new HandoverSubmittedEvent(this, saved));
            } catch (Exception e) {
                log.warn("Failed to publish HandoverSubmittedEvent for handover: {}", saved.getHandoverId(), e);
            }
        }

        return ArrivalHandoverResponse.fromDetail(saved, details, payments);
    }

    private void sendNotificationToFrontOffice(OpHandoverEntity handover, UserEntity actor, UUID assignedFoUserId) {
        List<UserEntity> foUsers;
        if (assignedFoUserId != null) {
            foUsers = userRepository.findById(assignedFoUserId)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            foUsers = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && 
                                (u.getRole().getRoleName().equalsIgnoreCase("FRONT_OFFICE") || 
                                 u.getRole().getRoleName().equalsIgnoreCase("FO")))
                    .toList();
        }

        String by = actor != null && actor.getFullName() != null ? actor.getFullName() : "Sales/Reservation";
        String bookingCode = handover.getBooking() != null ? handover.getBooking().getBookingCode() : "";

        for (UserEntity fo : foUsers) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(fo)
                    .title("Operational Handover Updated")
                    .message("Received updated operational handover from " + by + " for booking " + bookingCode)
                    .type("HANDOVER")
                    .relatedEntity("HANDOVER")
                    .relatedId(handover.getHandoverId())
                    .isRead(false)
                    .createdAt(OffsetDateTime.now())
                    .build();
            notificationRepository.save(notification);
        }
    }
}
