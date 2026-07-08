package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.request.CreateHandoverRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.application.usecase.timeline.CreateInteractionTimelineUseCase;
import com.novax.leadora.api.dto.request.CreateInteractionTimelineRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UC-20.1 — Create Operational Handover Use Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateHandoverUseCase {

    private final OpHandoverRepository opHandoverRepository;
    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CreateInteractionTimelineUseCase createInteractionTimelineUseCase;

    @Transactional
    public ArrivalHandoverResponse execute(CreateHandoverRequest request, UserEntity actor) {
        BookingEntity booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.getBookingId()));

        // BR-26 & E6-3.2: Check Guest Arrival Time
        if (LocalDate.now().isAfter(booking.getCheckInDate())) {
            throw new IllegalStateException("Cannot update past handovers.");
        }

        // BR-44: Booking is closed/cancelled/rejected
        String bStatus = booking.getStatus() != null ? booking.getStatus().name() : "";
        if (bStatus.equals("CANCELLED") || bStatus.equals("REJECTED") || bStatus.equals("CHECKED_OUT")) {
            throw new IllegalStateException("Booking is cancelled or completed, cannot create operational handover.");
        }

        // Check if handover already exists
        List<OpHandoverEntity> existing = opHandoverRepository.findByBooking_BookingId(request.getBookingId());
        if (!existing.isEmpty()) {
            throw new IllegalStateException("Operational handover for this booking already exists.");
        }

        HandoverStatus status;
        try {
            status = HandoverStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid handover status: " + request.getStatus());
        }

        if (status != HandoverStatus.DRAFT && status != HandoverStatus.SUBMITTED) {
            throw new IllegalStateException("Only Draft or Submitted statuses are allowed.");
        }

        // Enforce validations from UC-20.1 & UC-20.4 specification when submitting
        if (status == HandoverStatus.SUBMITTED) {
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

        OpHandoverEntity handover = OpHandoverEntity.builder()
                .booking(booking)
                .createdBy(actor)
                .updatedBy(actor)
                .status(status)
                .specialRequests(request.getSpecialRequests())
                .roomPreferences(request.getRoomPreferences())
                .vipNotes(request.getVipNotes())
                .operationalNotes(request.getOperationalNotes())
                .readinessStatus(ReadinessStatus.PENDING_REVIEW)
                .build();

        if (status == HandoverStatus.SUBMITTED) {
            handover.setSubmittedAt(OffsetDateTime.now());
        }

        OpHandoverEntity saved = opHandoverRepository.save(handover);

        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(booking.getBookingId());
        List<PaymentEntity> payments = paymentRepository.findByBooking_BookingId(booking.getBookingId());

        // BR-37: Write Slf4j Audit Log
        log.info("[AUDIT] Action: CREATE_OPERATIONAL_HANDOVER, TargetRecord: {}, Status: {}, CreatedBy: {}, Timestamp: {}",
                saved.getHandoverId(), status, actor != null ? actor.getUserId() : null, OffsetDateTime.now());

        if (status == HandoverStatus.SUBMITTED) {
            // Update Interaction Timeline
            try {
                CreateInteractionTimelineRequest timelineReq = new CreateInteractionTimelineRequest();
                timelineReq.setType("HANDOVER_SUBMISSION");
                timelineReq.setDescription("Operational handover submitted for booking: " + booking.getBookingCode());
                timelineReq.setOccurredAt(OffsetDateTime.now());
                if (booking.getQuotation() != null && booking.getQuotation().getDeal() != null) {
                    timelineReq.setDealId(booking.getQuotation().getDeal().getDealId());
                }
                if (booking.getCustomer() != null) {
                    timelineReq.setCustomerId(booking.getCustomer().getCustomerId());
                }
                createInteractionTimelineUseCase.execute(timelineReq);
            } catch (Exception e) {
                log.error("Lỗi ghi nhận Interaction Timeline: ", e);
            }

            // Notify Front Office Staff (POST-4)
            sendNotificationToFrontOffice(saved, actor, request.getAssignedFoUserId());
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
                    .title("New Operational Handover")
                    .message("Received new operational handover from " + by + " for booking " + bookingCode)
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
