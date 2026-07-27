package com.novax.leadora.application.usecase.reservation;

import com.novax.leadora.api.dto.request.UpdateStatusRequest;
import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.integration.email.EmailService;
import com.novax.leadora.application.usecase.booking.BookingStatusTransitionService;
import com.novax.leadora.application.usecase.booking.TransitionActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateReservationStatusUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final SalesFeedbackRepository salesFeedbackRepository;
    private final EmailService emailService;
    private final BookingStatusTransitionService bookingStatusTransitionService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public ReservationResponse execute(UUID id, UpdateStatusRequest request) {
        BookingEntity tempBooking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));

        BookingStatus oldStatus = tempBooking.getStatus();
        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid status: " + request.getStatus());
        }

        LocalDate oldCheckIn = tempBooking.getCheckInDate();
        LocalDate oldCheckOut = tempBooking.getCheckOutDate();

        LocalDate newCheckIn = request.getCheckInDate() != null ? request.getCheckInDate() : oldCheckIn;
        LocalDate newCheckOut = request.getCheckOutDate() != null ? request.getCheckOutDate() : oldCheckOut;

        boolean datesChanged = !newCheckIn.equals(oldCheckIn) || !newCheckOut.equals(oldCheckOut);
        if (datesChanged) {
            if (!newCheckOut.isAfter(newCheckIn)) {
                throw new BusinessRuleException("Check-out date must be after the check-in date");
            }
            // No capacity check on the new dates: this CRM owns no room inventory, and the
            // previous check invented capacity from the product name. The Front Office user
            // moving the dates is reading the real PMS as they do it.
        }

        // Call the centralized transition service
        BookingEntity booking = bookingStatusTransitionService.transition(
                id, newStatus, TransitionActor.FRONT_OFFICE, request.getReason());

        if (datesChanged) {
            booking.setCheckInDate(newCheckIn);
            booking.setCheckOutDate(newCheckOut);
            booking = bookingRepository.save(booking);
        }

        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(id);

        if (newStatus == BookingStatus.CHECKED_OUT) {
            // Trigger feedback invitation if customer has email and hasn't been invited yet
            if (booking.getCustomer() != null && org.springframework.util.StringUtils.hasText(booking.getCustomer().getEmail())) {
                boolean alreadyInvited = !salesFeedbackRepository.findByBooking_BookingId(id).isEmpty();
                if (!alreadyInvited) {
                    byte[] tokenBytes = new byte[32];
                    new java.security.SecureRandom().nextBytes(tokenBytes);
                    String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

                    SalesFeedbackEntity feedback = SalesFeedbackEntity.builder()
                            .customer(booking.getCustomer())
                            .booking(booking)
                            .salesStaff(booking.getAssignedUser())
                            .reviewStatus(ReviewStatus.PENDING)
                            .feedbackToken(token)
                            .tokenExpiresAt(OffsetDateTime.now().plusDays(30))
                            .build();

                    salesFeedbackRepository.save(feedback);

                    String feedbackLink = frontendUrl + "/feedback/" + token;
                    try {
                        emailService.sendFeedbackInvitationEmail(
                                booking.getCustomer().getEmail(),
                                booking.getCustomer().getFullName(),
                                feedbackLink
                        );
                        log.info("Feedback invitation successfully generated and sent for booking: {}. Link: {}", id, feedbackLink);
                    } catch (Exception e) {
                        log.error("Failed to send feedback invitation email for booking: {}. Link was: {}", id, feedbackLink, e);
                    }
                }
            }
        }

        // BR-37: Write Slf4j Audit Log
        log.info("[AUDIT] Action: UPDATE_RESERVATION_STATUS, TargetRecord: {}, OldValue: {}, NewValue: {}, OldCheckIn: {}, NewCheckIn: {}, OldCheckOut: {}, NewCheckOut: {}, Reason: {}, Timestamp: {}",
                id, oldStatus, newStatus, oldCheckIn, newCheckIn, oldCheckOut, newCheckOut, request.getReason(), OffsetDateTime.now());

        return ReservationResponse.from(booking, details);
    }
}
