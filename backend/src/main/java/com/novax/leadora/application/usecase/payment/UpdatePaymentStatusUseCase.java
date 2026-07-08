package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.api.dto.request.UpdatePaymentStatusRequest;
import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UC-21.4 — Update Payment Status Use Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePaymentStatusUseCase {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public PaymentResponse execute(UUID paymentId, UpdatePaymentStatusRequest request, UserEntity actor) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found", paymentId));

        BookingEntity booking = payment.getBooking();
        if (booking == null) {
            throw new IllegalStateException("Payment transaction is not linked to any booking.");
        }

        // BR-44: Check if booking is cancelled or checked out
        String bStatus = booking.getStatus() != null ? booking.getStatus().name() : "";
        if (bStatus.equals("CANCELLED") || bStatus.equals("CHECKED_OUT")) {
            throw new IllegalStateException("Booking is cancelled or checked out, cannot update payment.");
        }

        PaymentStatus newStatus = request.getStatus();

        // RBAC: FRONT_OFFICE receptionist can only mark payments as PAID (skip for system actor == null)
        if (actor != null && actor.getRole() != null) {
            String roleName = actor.getRole().getRoleName();
            if ("FRONT_OFFICE".equalsIgnoreCase(roleName)) {
                if (newStatus != PaymentStatus.PAID) {
                    throw new IllegalStateException("Front office staff can only mark payments as PAID.");
                }
            }
        }

        // BR-29 & E4-1.1: Missing verification note when updating to PAID (skip check for system actor == null)
        if (newStatus == PaymentStatus.PAID) {
            if (actor != null && !StringUtils.hasText(request.getVerificationNote())) {
                throw new IllegalStateException("Missing verification note");
            }
            payment.setPaidAt(OffsetDateTime.now());
            payment.setVerificationNote(StringUtils.hasText(request.getVerificationNote()) 
                    ? request.getVerificationNote() 
                    : "Confirmed automatically by System.");

            // POST-2: Update booking status to CONFIRMED if it was PENDING
            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                log.info("Booking status updated to CONFIRMED for Booking: {} after payment success", booking.getBookingId());
            }
        }

        payment.setStatus(newStatus);
        PaymentEntity saved = paymentRepository.save(payment);

        log.info("[AUDIT] Action: UPDATE_PAYMENT_STATUS, PaymentId: {}, Status: {}, UpdatedBy: {}",
                saved.getPaymentId(), saved.getStatus(), actor != null ? actor.getUserId() : null);

        return PaymentResponse.from(saved);
    }
}
