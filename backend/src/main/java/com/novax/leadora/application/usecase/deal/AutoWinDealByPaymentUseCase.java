package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoWinDealByPaymentUseCase {

    private final DealRepository dealRepository;
    private final SystemAuditLogService auditLogService;
    private final DealWorkflowResolver dealWorkflowResolver;

    @Transactional
    public void execute(PaymentEntity payment, UserEntity actor) {
        // 1. Idempotency Check & Invariant Verification
        if (payment.getStatus() != PaymentStatus.PAID) {
            return;
        }

        BookingEntity booking = payment.getBooking();
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED) {
            return;
        }

        QuotationEntity quotation = booking.getQuotation();
        if (quotation == null || quotation.getDeal() == null) {
            return;
        }

        DealEntity deal = quotation.getDeal();
        if (deal.getStatus() == DealStatus.WON) {
            // Already WON, skip to maintain idempotency (prevent duplicate audit logs)
            return;
        }

        final UUID dealId = deal.getDealId();
        deal = dealRepository.findByIdForUpdate(dealId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal record not found", dealId));

        // 2. Verify Payment belongs to the active Deal workflow
        QuotationEntity activeQuotation = dealWorkflowResolver.resolveActiveQuotation(deal.getDealId()).orElse(null);
        if (activeQuotation == null || !activeQuotation.getQuotationId().equals(quotation.getQuotationId())) {
            log.error("Payment belongs to inactive Quotation. Throwing exception to rollback.");
            throw new BusinessException("DEAL_STATE_CONFLICT", "Cannot auto-win Deal from an inactive Quotation.", HttpStatus.CONFLICT);
        }

        BookingEntity activeBooking = dealWorkflowResolver.resolveActiveBooking(activeQuotation.getQuotationId()).orElse(null);
        if (activeBooking == null || !activeBooking.getBookingId().equals(booking.getBookingId())) {
            log.error("Payment belongs to inactive Booking. Throwing exception to rollback.");
            throw new BusinessException("DEAL_STATE_CONFLICT", "Cannot auto-win Deal from an inactive Booking.", HttpStatus.CONFLICT);
        }

        if (deal.getStatus() == DealStatus.LOST) {
            log.error("Conflict detected: Payment confirmed for a LOST deal {}", deal.getDealId());
            throw new BusinessException("DEAL_STATE_CONFLICT", "Cannot auto-win a Deal that is already marked LOST.", HttpStatus.CONFLICT);
        }

        if (deal.getStatus() != DealStatus.OPEN) {
            log.error("Conflict detected: Payment confirmed for deal {} in non-OPEN status: {}", deal.getDealId(), deal.getStatus());
            throw new BusinessException("DEAL_STATE_CONFLICT", "Cannot auto-win a Deal that is in status: " + deal.getStatus(), HttpStatus.CONFLICT);
        }

        // 4. Perform transition
        DealStatus oldStatus = deal.getStatus();
        deal.setStatus(DealStatus.WON);
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);
        dealRepository.save(deal);

        log.info("[AUTO-WIN] Deal {} successfully transitioned to WON via Payment {}", deal.getDealId(), payment.getPaymentId());

        // 5. Log Audit Event
        auditLogService.log("DEAL", "Deal", deal.getDealId(),
                "AUTO_CLOSED_WON", actor,
                oldStatus != null ? oldStatus.name() : "OPEN", "WON",
                "Auto-closed Won: Payment " + payment.getPaymentId() + " confirmed for Booking " + booking.getBookingCode());
    }
}
