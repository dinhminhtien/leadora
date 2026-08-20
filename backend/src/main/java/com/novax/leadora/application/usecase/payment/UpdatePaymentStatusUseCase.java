package com.novax.leadora.application.usecase.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.api.dto.request.UpdatePaymentStatusRequest;
import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.application.usecase.deal.DealWorkflowResolver;
import com.novax.leadora.application.usecase.deal.AutoWinDealByPaymentUseCase;
import com.novax.leadora.application.usecase.roomrequest.RoomConfirmationReader;
import com.novax.leadora.application.usecase.roomrequest.RoomRequestNotifier;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
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
    private final DealRepository dealRepository;
    private final DealWorkflowResolver dealWorkflowResolver;
    private final AutoWinDealByPaymentUseCase autoWinDealByPaymentUseCase;
    private final RoomConfirmationReader roomConfirmationReader;
    private final RoomRequestNotifier roomRequestNotifier;
    private final AuditCorrectionService auditCorrectionService;
    private final ObjectMapper objectMapper;

    /** Same family BookingStatusTransitionService corrects against, so both paths share one chain. */
    private static final List<ActivityLogType> BOOKING_FAMILY_TYPES = List.of(
            ActivityLogType.BOOKING_CREATED,
            ActivityLogType.BOOKING_CONFIRMED,
            ActivityLogType.BOOKING_CANCELLED,
            ActivityLogType.BOOKING_UPDATED);

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> activeTxCodes = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public PaymentResponse execute(UUID paymentId, UpdatePaymentStatusRequest request, UserEntity actor) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment record not found."));

        BookingEntity booking = payment.getBooking();
        if (booking == null) {
            throw new IllegalStateException("Payment transaction is not linked to any booking.");
        }

        // Lock Deal first if present (Lock ordering: Deal -> Booking)
        QuotationEntity quotation = booking.getQuotation();
        DealEntity deal = quotation != null ? quotation.getDeal() : null;
        if (deal != null) {
            final UUID dealId = deal.getDealId();
            deal = dealRepository.findByIdForUpdate(dealId)
                    .orElseThrow(() -> new ResourceNotFoundException("Deal record not found", dealId));
        }

        // Lock Booking second
        final UUID bookingId = booking.getBookingId();
        booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking record not found", bookingId));

        // BR-44: Check if booking is cancelled or checked out
        String bStatus = booking.getStatus() != null ? booking.getStatus().name() : "";
        if (bStatus.equals("CANCELLED") || bStatus.equals("CHECKED_OUT")) {
            throw new IllegalStateException("Booking is cancelled or checked out, cannot update payment.");
        }
        if (bStatus.equals("REJECTED")) {
            throw new IllegalStateException("Booking is rejected, cannot update payment.");
        }

        PaymentStatus oldStatus = payment.getStatus();
        PaymentStatus newStatus = request.getStatus();

        // BLOCK: Do not allow activating CANCELLED or EXPIRED payments to PAID
        if (newStatus == PaymentStatus.PAID) {
            if (oldStatus == PaymentStatus.CANCELLED || oldStatus == PaymentStatus.EXPIRED) {
                throw new IllegalStateException("This payment request is CANCELLED or EXPIRED and cannot be confirmed.");
            }
        }

        // Prevent duplicate gateway transaction codes (Double-Spending protection)
        if (newStatus == PaymentStatus.PAID && oldStatus != PaymentStatus.PAID) {
            String rawRef = request.getVerificationNote();
            if (actor == null) { // Automated flow from Scheduler
                rawRef = payment.getGatewayTransactionId();
            }
            if (StringUtils.hasText(rawRef)) {
                String txCode = rawRef.replaceAll("[^a-zA-Z0-9_-]", "").toUpperCase();
                if (txCode.isEmpty()) {
                    throw new IllegalStateException("Transaction verification code is empty or invalid.");
                }

                if (activeTxCodes.putIfAbsent(txCode, Boolean.TRUE) != null) {
                    throw new BusinessException("DUPLICATE_TXN", "The bank transaction is currently being processed by another thread.", HttpStatus.CONFLICT);
                }
                try {
                    java.util.Optional<PaymentEntity> duplicate = paymentRepository.findByGatewayTransactionId(txCode);
                    if (duplicate.isPresent() && !duplicate.get().getPaymentId().equals(paymentId)) {
                        throw new BusinessException("DUPLICATE_TXN", "This bank transaction has already been verified for another payment.", HttpStatus.CONFLICT);
                    }
                    payment.setGatewayTransactionId(txCode);
                } finally {
                    activeTxCodes.remove(txCode);
                }
            }
        }

        // Check business rules before making the transition
        if (oldStatus != PaymentStatus.PAID && newStatus == PaymentStatus.PAID) {
            if (deal != null) {
                // BR-DEAL-LOST-02: Confirmed payment on LOST deal triggers transaction rollback
                if (deal.getStatus() == DealStatus.LOST) {
                    throw new BusinessException("DEAL_STATE_CONFLICT", "Cannot confirm payment for a LOST deal.", HttpStatus.CONFLICT);
                }

                // BR-PAYMENT-04: Confirmed payment on Inactive Booking triggers transaction rollback
                QuotationEntity activeQuotation = dealWorkflowResolver.resolveActiveQuotation(deal.getDealId()).orElse(null);
                BookingEntity activeBooking = activeQuotation == null ? null 
                        : dealWorkflowResolver.resolveActiveBooking(activeQuotation.getQuotationId()).orElse(null);

                if (activeBooking == null || !activeBooking.getBookingId().equals(booking.getBookingId())) {
                    throw new BusinessException("WORKFLOW_CONSTRAINT_VIOLATION", "Cannot confirm payment on an inactive Booking.", HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
        }

        // RBAC: the arrival desk can only mark payments as PAID (skip for system actor == null).
        //
        // Both spellings, because the `roles` table holds `FO` and `FRONT_OFFICE` is the legacy
        // name (see RbacRoles). Matching only the legacy one meant this rule never fired for a real
        // Front Office user: the desk that is capped at "confirm paid" in the UI could set any
        // status at all through the API, and the cap read as enforced because the button was hidden.
        if (actor != null && actor.getRole() != null) {
            String roleName = actor.getRole().getRoleName();
            if ("FO".equalsIgnoreCase(roleName) || "FRONT_OFFICE".equalsIgnoreCase(roleName)) {
                if (newStatus != PaymentStatus.PAID) {
                    throw new IllegalStateException("Front office staff can only mark payments as PAID.");
                }
            }
        }

        // BR-29 & E4-1.1: Missing verification note when updating to PAID (skip check for system actor == null)
        if (newStatus == PaymentStatus.PAID) {
            if (actor != null && !StringUtils.hasText(request.getVerificationNote())) {
                throw new IllegalStateException("Missing verification note.");
            }
            payment.setPaidAt(OffsetDateTime.now());
            payment.setVerificationNote(StringUtils.hasText(request.getVerificationNote()) 
                    ? request.getVerificationNote() 
                    : "Confirmed automatically by System.");

            // POST-2: a paid deposit confirms the booking. Room confirmation is advisory, so
            // it does not hold the booking back — but when the Reservation team has not
            // confirmed the rooms, they are told money has landed on it.
            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                log.info("Booking status updated to CONFIRMED for Booking: {} after payment success",
                        booking.getBookingId());
                recordBookingConfirmedActivity(booking, payment);

                if (!roomConfirmationReader.isRoomConfirmed(booking.getQuotation())) {
                    roomRequestNotifier.paymentReceivedWithoutRoomConfirmation(booking);
                }
            }
        }

        payment.setStatus(newStatus);
        PaymentEntity saved = paymentRepository.save(payment);

        log.info("[AUDIT] Action: UPDATE_PAYMENT_STATUS, PaymentId: {}, Status: {}, UpdatedBy: {}",
                saved.getPaymentId(), saved.getStatus(), actor != null ? actor.getUserId() : null);

        // Auto-win trigger
        if (oldStatus != PaymentStatus.PAID && newStatus == PaymentStatus.PAID) {
            if (deal != null) {
                autoWinDealByPaymentUseCase.execute(saved, actor);
            }
        }

        return PaymentResponse.from(saved);
    }

    /**
     * A deposit landing confirms the booking here rather than through
     * {@code BookingStatusTransitionService}, and for a long time that meant the most common
     * confirmation path in the system left no {@code BOOKING_CONFIRMED} row behind: the audit trail
     * showed bookings that were suddenly CONFIRMED with nothing saying when or why, and any report
     * asking "how many bookings were confirmed this period" had to fall back on guesswork.
     *
     * <p>Routed through {@link AuditCorrectionService#correctPriorActivity} — not a bare publish —
     * so this row joins the same correction chain the manual transitions build, and a later
     * cancellation voids the right head.
     *
     * <p><b>Not</b> best-effort, and deliberately not wrapped in a swallow-everything catch. The
     * correction listener is a plain {@code @EventListener} that runs in this very transaction and
     * rethrows to force a rollback, so catching here would not save the payment — it would only
     * hide the cause and let the commit fail later with an opaque {@code UnexpectedRollbackException}
     * after this method had already reported success. On a money path, an audit trail that silently
     * did not happen is the worse outcome: if the log cannot be written, the confirmation does not
     * stand, and the caller gets the real reason.
     */
    private void recordBookingConfirmedActivity(BookingEntity booking, PaymentEntity payment) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("actor", "SYSTEM")
                .put("previousStatus", BookingStatus.PENDING.name())
                .put("newStatus", BookingStatus.CONFIRMED.name())
                .put("trigger", "PAYMENT_PAID")
                .put("paymentId", String.valueOf(payment.getPaymentId()));
        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.BOOKING_CONFIRMED)
                .entityType(EntityType.BOOKING)
                .entityId(booking.getBookingId())
                .summary("Booking confirmed automatically after payment was marked PAID")
                .payload(payload)
                .reason("Deposit payment confirmed")
                .build();
        auditCorrectionService.correctPriorActivity(booking.getBookingId(), BOOKING_FAMILY_TYPES, command);
    }
}
