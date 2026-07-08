package com.novax.leadora.application.usecase.reservation;

import com.novax.leadora.api.dto.request.UpdateStatusRequest;
import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.integration.email.EmailService;
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

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public ReservationResponse execute(UUID id, UpdateStatusRequest request) {
        BookingEntity booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));

        BookingStatus oldStatus = booking.getStatus();
        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Trạng thái không hợp lệ: " + request.getStatus());
        }

        LocalDate oldCheckIn = booking.getCheckInDate();
        LocalDate oldCheckOut = booking.getCheckOutDate();

        LocalDate newCheckIn = request.getCheckInDate() != null ? request.getCheckInDate() : oldCheckIn;
        LocalDate newCheckOut = request.getCheckOutDate() != null ? request.getCheckOutDate() : oldCheckOut;

        // Check if dates changed, validate room availability
        if (!newCheckIn.equals(oldCheckIn) || !newCheckOut.equals(oldCheckOut)) {
            if (!newCheckOut.isAfter(newCheckIn)) {
                throw new BusinessRuleException("Ngày trả phòng phải sau ngày nhận phòng");
            }

            List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(id);
            for (BookingDetailEntity detail : details) {
                if (detail.getProductService() != null) {
                    validateRoomAvailability(detail.getProductService().getProductId(), detail.getProductService().getName(), newCheckIn, newCheckOut, detail.getQuantity(), id);
                }
            }

            booking.setCheckInDate(newCheckIn);
            booking.setCheckOutDate(newCheckOut);
        }

        // Apply new status
        booking.setStatus(newStatus);
        
        // Update inventory statuses in booking details depending on new booking status
        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(id);
        if (newStatus == BookingStatus.CHECKED_IN) {
            for (BookingDetailEntity detail : details) {
                detail.setInventoryStatus(InventoryStatus.ALLOCATED);
                bookingDetailRepository.save(detail);
            }
        } else if (newStatus == BookingStatus.CHECKED_OUT) {
            for (BookingDetailEntity detail : details) {
                detail.setInventoryStatus(InventoryStatus.RELEASED);
                bookingDetailRepository.save(detail);
            }
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

                    String feedbackLink = frontendUrl + "/feedback/public/" + token;
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
        } else if (newStatus == BookingStatus.CANCELLED || newStatus == BookingStatus.REJECTED) {
            for (BookingDetailEntity detail : details) {
                detail.setInventoryStatus(InventoryStatus.RELEASED);
                bookingDetailRepository.save(detail);
            }
        }

        BookingEntity saved = bookingRepository.save(booking);

        // BR-37: Write Slf4j Audit Log
        log.info("[AUDIT] Action: UPDATE_RESERVATION_STATUS, TargetRecord: {}, OldValue: {}, NewValue: {}, OldCheckIn: {}, NewCheckIn: {}, OldCheckOut: {}, NewCheckOut: {}, Reason: {}, Timestamp: {}",
                id, oldStatus, newStatus, oldCheckIn, newCheckIn, oldCheckOut, newCheckOut, request.getReason(), OffsetDateTime.now());

        return ReservationResponse.from(saved, details);
    }

    private void validateRoomAvailability(UUID productId, String productName, LocalDate checkInDate, LocalDate checkOutDate, int requestQty, UUID currentBookingId) {
        List<BookingStatus> activeStatuses = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
        List<BookingEntity> allBookings = bookingRepository.findAll();
        
        int totalBooked = 0;
        for (BookingEntity booking : allBookings) {
            // Skip the current booking being updated to avoid self-overlap conflict
            if (booking.getBookingId().equals(currentBookingId)) {
                continue;
            }

            if (activeStatuses.contains(booking.getStatus())) {
                if (booking.getCheckInDate().isBefore(checkOutDate) && booking.getCheckOutDate().isAfter(checkInDate)) {
                    List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(booking.getBookingId());
                    for (BookingDetailEntity detail : details) {
                        if (detail.getProductService() != null && detail.getProductService().getProductId().equals(productId)) {
                            totalBooked += detail.getQuantity();
                        }
                    }
                }
            }
        }

        int capacity = 10;
        if (productName != null) {
            if (productName.contains("Suite")) {
                capacity = 5;
            } else if (productName.contains("Deluxe")) {
                capacity = 10;
            } else if (productName.contains("Standard")) {
                capacity = 15;
            }
        }

        if ((totalBooked + requestQty) > capacity) {
            throw new BusinessRuleException(String.format("Dates unavailable. Inventory conflict for room type '%s'. Available: %d, Requested: %d",
                    productName, (capacity - totalBooked), requestQty));
        }
    }
}
