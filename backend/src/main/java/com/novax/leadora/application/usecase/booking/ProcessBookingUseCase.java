package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.request.ProcessBookingRequest;
import com.novax.leadora.api.dto.response.BookingDetailResponse;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.novax.leadora.application.usecase.email.event.BookingConfirmedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessBookingUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingStatusTransitionService bookingStatusTransitionService;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final SystemAuditLogService systemAuditLogService;
    private final DealWorkflowSyncService dealWorkflowSyncService;

    @Transactional
    public BookingResponse execute(UUID bookingId, ProcessBookingRequest request) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with ID: " + bookingId));
        BookingStatus oldStatus = booking.getStatus();

        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + request.getStatus());
        }

        // The actor's role decides which transitions are legal — only the Reservation team
        // (and its MANAGER/ADMIN escalation path) may CONFIRM, since only they know
        // whether the rooms exist. Resolved from the authenticated user, never the body.
        UserEntity actor = currentUserProvider.resolve(null);

        BookingEntity saved = bookingStatusTransitionService.transition(
                bookingId, newStatus, TransitionActor.fromUser(actor), request.getStatusReason());

        if (saved.getQuotation() != null && saved.getQuotation().getDeal() != null) {
            dealWorkflowSyncService.syncPipelineStage(saved.getQuotation().getDeal().getDealId());
        }

        // The rejection reason is already persisted structurally as booking.statusReason by
        // the transition service (and read back from there by the UI), so it is no longer
        // also appended into specialRequests — that duplicated customer-facing notes.

        // UC-17.2: the Reservation team answered, so the BOOKING_CONFIRM clock stops here.
        // Uses entityType BOOKING to match the tracking rows started by
        // ConvertToBookingUseCase.
        try {
            resolveSlaBreachUseCase.executeByEntity("BOOKING", bookingId);
        } catch (Exception e) {
            log.warn("SLA auto-resolve failed for booking {}: {}", bookingId, e.getMessage());
        }

        systemAuditLogService.log("BOOKING", "BOOKING", bookingId, newStatus.name(), actor,
                oldStatus != null ? oldStatus.name() : null, newStatus.name(), request.getStatusReason());

        // UC-15.1: notify the assigned staff of the booking status decision
        if (saved.getAssignedUser() != null) {
            try {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(saved.getAssignedUser())
                        .title("Booking " + newStatus.name())
                        .message("Booking " + saved.getBookingCode() + " is now " + newStatus.name() + ".")
                        .type("BOOKING_UPDATE")
                        .relatedEntity("BOOKING")
                        .relatedId(bookingId)
                        .build();
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.warn("Booking-update notification failed for booking {}: {}", bookingId, e.getMessage());
            }
        }

        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(bookingId);

        if (oldStatus != BookingStatus.CONFIRMED && newStatus == BookingStatus.CONFIRMED) {
            try {
                eventPublisher.publishEvent(new BookingConfirmedEvent(saved, details));
            } catch (Exception e) {
                log.warn("Booking confirmation email event failed for booking code {}: {}", saved.getBookingCode(), e.getMessage());
            }
        }

        List<BookingDetailResponse> detailResponses = details.stream()
                .map(BookingDetailResponse::from)
                .collect(Collectors.toList());

        return BookingResponse.from(saved, detailResponses);
    }
}
