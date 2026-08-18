package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStatusTransitionService {
    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;
    private final AuditCorrectionService auditCorrectionService;
    private final ObjectMapper objectMapper;

    private static final List<ActivityLogType> BOOKING_FAMILY_TYPES = List.of(
            ActivityLogType.BOOKING_CREATED,
            ActivityLogType.BOOKING_CONFIRMED,
            ActivityLogType.BOOKING_CANCELLED,
            ActivityLogType.BOOKING_UPDATED);

    // Sales owns the deal, not the room: they may withdraw a booking, never confirm
    // one.
    private static final Map<BookingStatus, Set<BookingStatus>> SALES_TRANSITIONS = Map.of(
            PENDING, Set.of(CANCELLED),
            CONFIRMED, Set.of(CANCELLED));

    // The Reservation team decides whether the rooms exist, so only they may
    // CONFIRM.
    private static final Map<BookingStatus, Set<BookingStatus>> RESERVATION_TRANSITIONS = Map.of(
            PENDING, Set.of(CONFIRMED, REJECTED, CANCELLED),
            CONFIRMED, Set.of(NO_SHOW, CANCELLED));

    // The arrival desk moves a confirmed booking through the stay.
    private static final Map<BookingStatus, Set<BookingStatus>> FRONT_OFFICE_TRANSITIONS = Map.of(
            PENDING, Set.of(CANCELLED),
            CONFIRMED, Set.of(CHECKED_IN, CANCELLED),
            CHECKED_IN, Set.of(CHECKED_OUT));

    private static Map<BookingStatus, Set<BookingStatus>> laneFor(TransitionActor actor) {
        return switch (actor) {
            case RESERVATION -> RESERVATION_TRANSITIONS;
            case FRONT_OFFICE -> FRONT_OFFICE_TRANSITIONS;
            case SALES -> SALES_TRANSITIONS;
        };
    }

    @Transactional
    public BookingEntity transition(UUID bookingId, BookingStatus newStatus, TransitionActor actor, String reason) {
        // 1. SELECT FOR UPDATE to prevent race conditions (C5)
        BookingEntity booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        BookingStatus oldStatus = booking.getStatus();

        // 2. Validate the transition against this actor's lane
        Set<BookingStatus> allowed = laneFor(actor).get(oldStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BusinessException("BOOKING_TRANSITION_INVALID",
                    String.format("Transition from %s to %s is not allowed for this actor.", oldStatus, newStatus),
                    HttpStatus.CONFLICT);
        }

        // 3. Guard C5: Block CANCEL/REJECT if there is a PAID payment (NO_SHOW is
        // intentionally allowed)
        if (newStatus == BookingStatus.CANCELLED || newStatus == BookingStatus.REJECTED) {
            boolean hasPaidPayment = paymentRepository.existsByBooking_BookingIdAndStatus(bookingId,
                    PaymentStatus.PAID);
            if (hasPaidPayment) {
                throw new BusinessException("PAYMENT_CONFLICT",
                        "Không thể hủy booking đã thanh toán. Vui lòng liên hệ kế toán để xử lý hoàn tiền ngoài hệ thống trước khi yêu cầu hỗ trợ kỹ thuật cập nhật trạng thái.",
                        HttpStatus.CONFLICT);
            }
        }

        // 4. Update status & status change reason
        booking.setStatus(newStatus);
        if (reason != null && !reason.isBlank()) {
            booking.setStatusReason(reason);
        }

        // 5. Sync inventory status in booking details
        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(bookingId);
        if (newStatus == BookingStatus.CHECKED_IN) {
            details.forEach(d -> d.setInventoryStatus(InventoryStatus.ALLOCATED));
        } else if (newStatus == BookingStatus.CHECKED_OUT || newStatus == BookingStatus.CANCELLED
                || newStatus == BookingStatus.REJECTED || newStatus == BookingStatus.NO_SHOW) {
            details.forEach(d -> d.setInventoryStatus(InventoryStatus.RELEASED));
        }
        bookingDetailRepository.saveAll(details);

        BookingEntity savedBooking = bookingRepository.save(booking);

        ActivityLogType logType = switch (newStatus) {
            case CONFIRMED -> ActivityLogType.BOOKING_CONFIRMED;
            case CANCELLED -> ActivityLogType.BOOKING_CANCELLED;
            default -> ActivityLogType.BOOKING_UPDATED;
        };

        if (newStatus == BookingStatus.CANCELLED) {
            try {
                auditCorrectionService.voidPriorActivity(bookingId, BOOKING_FAMILY_TYPES, reason);
            } catch (Exception e) {
                log.warn("Failed to void booking status transition activity: {}", e.getMessage());
            }
        } else {
            try {
                ObjectNode payload = objectMapper.createObjectNode()
                        .put("actor", actor.name())
                        .put("previousStatus", oldStatus.name())
                        .put("newStatus", newStatus.name());
                if (reason != null) {
                    payload.put("reason", reason);
                }
                ActivityLogCommand command = ActivityLogCommand.builder()
                        .activityType(logType)
                        .entityType(EntityType.BOOKING)
                        .entityId(savedBooking.getBookingId())
                        .summary("Booking status updated to " + newStatus)
                        .payload(payload)
                        .reason(reason)
                        .build();
                auditCorrectionService.correctPriorActivity(bookingId, BOOKING_FAMILY_TYPES, command);
            } catch (Exception e) {
                log.warn("Failed to publish booking status transition activity: {}", e.getMessage());
            }
        }

        return savedBooking;
    }
}
