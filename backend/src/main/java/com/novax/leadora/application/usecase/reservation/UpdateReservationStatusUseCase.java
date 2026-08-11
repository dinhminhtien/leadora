package com.novax.leadora.application.usecase.reservation;

import com.novax.leadora.api.dto.request.UpdateStatusRequest;
import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.application.usecase.email.event.FeedbackInvitationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.novax.leadora.application.usecase.booking.BookingStatusTransitionService;
import com.novax.leadora.application.usecase.booking.TransitionActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ApplicationEventPublisher eventPublisher;
    private final BookingStatusTransitionService bookingStatusTransitionService;
    private final PaymentRepository paymentRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public ReservationResponse execute(UUID id, UpdateStatusRequest request) {
        BookingEntity tempBooking = bookingRepository.findByIdForUpdate(id)
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
        }

        // Call the centralized transition service
        BookingEntity booking = bookingStatusTransitionService.transition(
                id, newStatus, TransitionActor.FRONT_OFFICE, request.getReason());

        BigDecimal originalTotal = booking.getTotalAmount();

        if (datesChanged) {
            booking.setCheckInDate(newCheckIn);
            booking.setCheckOutDate(newCheckOut);
            
            // Recalculate nights and room charges in Java Code
            long nights = java.time.temporal.ChronoUnit.DAYS.between(newCheckIn, newCheckOut);
            BigDecimal newTotal = BigDecimal.ZERO;
            
            List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(id);
            for (BookingDetailEntity d : details) {
                d.setNights((int) nights);
                BigDecimal lineTotal = d.getUnitPrice()
                        .multiply(BigDecimal.valueOf(d.getQuantity()))
                        .multiply(BigDecimal.valueOf(nights));
                d.setLineTotal(lineTotal);
                newTotal = newTotal.add(lineTotal);
                bookingDetailRepository.save(d);
            }
            booking.setTotalAmount(newTotal);
            booking = bookingRepository.save(booking);

            // Additional payment request if staying nights increased
            if (newTotal.compareTo(originalTotal) > 0) {
                BigDecimal deltaAmount = newTotal.subtract(originalTotal);
                PaymentEntity payment = PaymentEntity.builder()
                        .booking(booking)
                        .amount(deltaAmount)
                        .paymentType(PaymentType.PARTIAL)
                        .status(PaymentStatus.PENDING)
                        .paymentMethod("TRANSFER")
                        .dueDate(LocalDate.now().plusDays(7))
                        .verificationNote("Additional payment for stay date adjustment (" + oldCheckIn + " -> " + newCheckIn + ", " + oldCheckOut + " -> " + newCheckOut + ")")
                        .gatewayProvider("MBBANK")
                        .build();
                PaymentEntity savedPayment = paymentRepository.save(payment);
                long amountInVnd = Math.round(deltaAmount.doubleValue());
                String qrCodeUrl = "https://img.vietqr.io/image/MB-22224102004-compact2.png?amount=" + amountInVnd 
                        + "&addInfo=LEADORAPAY" + savedPayment.getPaymentId();
                savedPayment.setQrCodeUrl(qrCodeUrl);
                paymentRepository.save(savedPayment);
                log.info("[AUDIT] Auto-generated delta payment {} for booking stay extension.", savedPayment.getPaymentId());
            }
            
            // Refund record if staying nights decreased
            if (newTotal.compareTo(originalTotal) < 0) {
                BigDecimal refundAmount = originalTotal.subtract(newTotal);
                PaymentEntity refund = PaymentEntity.builder()
                        .booking(booking)
                        .amount(refundAmount)
                        .paymentType(PaymentType.PARTIAL)
                        .status(PaymentStatus.REFUNDED)
                        .paymentMethod("CASH")
                        .dueDate(LocalDate.now())
                        .verificationNote("Refund for shortened stay date adjustment (" + oldCheckIn + " -> " + newCheckIn + ", " + oldCheckOut + " -> " + newCheckOut + ")")
                        .gatewayProvider("SYSTEM")
                        .build();
                paymentRepository.save(refund);
                log.info("[AUDIT] Auto-generated refund record {} for booking stay shortening.", refund.getPaymentId());
            }
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
                    eventPublisher.publishEvent(new FeedbackInvitationEvent(
                            booking.getCustomer().getEmail(),
                            booking.getCustomer().getFullName(),
                            feedbackLink
                    ));
                    log.info("Feedback invitation event published for booking: {}. Link: {}", id, feedbackLink);
                }
            }
        }

        // BR-37: Write Slf4j Audit Log
        log.info("[AUDIT] Action: UPDATE_RESERVATION_STATUS, TargetRecord: {}, OldValue: {}, NewValue: {}, OldCheckIn: {}, NewCheckIn: {}, OldCheckOut: {}, NewCheckOut: {}, Reason: {}, Timestamp: {}",
                id, oldStatus, newStatus, oldCheckIn, newCheckIn, oldCheckOut, newCheckOut, request.getReason(), OffsetDateTime.now());

        return ReservationResponse.from(booking, details);
    }
}
