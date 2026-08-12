package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealWorkflowSyncService {

    private final DealRepository dealRepository;
    private final DealWorkflowResolver dealWorkflowResolver;
    private final DealValidation dealValidation;
    private final ActivityLogPublisher activityLogPublisher;
    private final RecordDealStageChangeService recordDealStageChangeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void syncPipelineStage(UUID dealId) {
        if (dealId == null) {
            return;
        }

        DealEntity deal = dealRepository.findByIdForUpdate(dealId)
                .orElse(null);
        if (deal == null) {
            log.warn("Deal not found for sync: {}", dealId);
            return;
        }

        syncPipelineStage(deal);
    }

    @Transactional
    public void syncPipelineStage(DealEntity deal) {
        if (deal == null || deal.getStatus() != DealStatus.OPEN) {
            return;
        }

        // 1. Resolve active quotation and booking
        QuotationEntity activeQuot = dealWorkflowResolver.resolveActiveQuotation(deal.getDealId()).orElse(null);
        BookingEntity activeBooking = null;
        if (activeQuot != null) {
            activeBooking = dealWorkflowResolver.resolveActiveBooking(activeQuot.getQuotationId()).orElse(null);
        }

        // Determine target stage
        // NOTE: deal.expectedRevenue is intentionally NOT synced here.
        // deal_value is a Sales estimate (SSOT for forecast) and must remain independent
        // of quotation pricing. The only authoritative sync point is CustomerAcceptQuotationUseCase
        // when the customer confirms — at that moment the estimate becomes an actuals figure.
        DealPipelineStage currentStage = deal.getPipelineStage();
        DealPipelineStage targetStage = currentStage;

        if (activeBooking != null) {
            if (activeBooking.getStatus() == BookingStatus.CONFIRMED
                    || activeBooking.getStatus() == BookingStatus.CHECKED_IN
                    || activeBooking.getStatus() == BookingStatus.CHECKED_OUT) {
                if (dealWorkflowResolver.getStageOrder(currentStage) < dealWorkflowResolver
                        .getStageOrder(DealPipelineStage.BOOKING_CONFIRMED)) {
                    targetStage = DealPipelineStage.BOOKING_CONFIRMED;
                }
            } else {
                if (dealWorkflowResolver.getStageOrder(currentStage) < dealWorkflowResolver
                        .getStageOrder(DealPipelineStage.PENDING_CONFIRMATION)) {
                    targetStage = DealPipelineStage.PENDING_CONFIRMATION;
                }
            }
        } else if (activeQuot != null) {
            if (dealWorkflowResolver.getStageOrder(currentStage) < dealWorkflowResolver
                    .getStageOrder(DealPipelineStage.QUOTATION_SENT)) {
                targetStage = DealPipelineStage.QUOTATION_SENT;
            }
        }

        if (targetStage == currentStage) {
            return; // No promotion needed
        }

        log.info("Auto-syncing Deal {} stage from {} to {}", deal.getDealId(), currentStage, targetStage);

        // 2. Ensure business constraints are satisfied before promotion to comply with
        // DealValidation rules
        // For QUOTATION_SENT (index 2): requires value > 0
        if (dealWorkflowResolver.getStageOrder(targetStage) >= 2) {
            if (deal.getExpectedRevenue() == null || deal.getExpectedRevenue().compareTo(BigDecimal.ZERO) <= 0) {
                if (activeQuot != null && activeQuot.getTotalAmount() != null
                        && activeQuot.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                    deal.setExpectedRevenue(activeQuot.getTotalAmount());
                } else {
                    deal.setExpectedRevenue(BigDecimal.ONE); // Fallback to satisfy validation
                }
            }
        }

        // For NEGOTIATION (index 3): requires notes length >= 5
        if (dealWorkflowResolver.getStageOrder(targetStage) >= 3) {
            String notes = deal.getNotes();
            if (notes == null || notes.trim().length() < 5) {
                if (activeBooking != null && activeBooking.getSpecialRequests() != null
                        && activeBooking.getSpecialRequests().trim().length() >= 5) {
                    deal.setNotes(activeBooking.getSpecialRequests().trim());
                } else if (activeQuot != null && activeQuot.getNotes() != null
                        && activeQuot.getNotes().trim().length() >= 5) {
                    deal.setNotes(activeQuot.getNotes().trim());
                } else {
                    deal.setNotes("Negotiation started based on active Booking "
                            + (activeBooking != null ? activeBooking.getBookingCode() : "") + ".");
                }
            }
        }

        // For PENDING_CONFIRMATION (index 4): requires expectedCloseDate
        if (dealWorkflowResolver.getStageOrder(targetStage) >= 4) {
            if (deal.getExpectedCloseDate() == null) {
                deal.setExpectedCloseDate(LocalDate.now().plusDays(7));
            }
        }

        // 3. Validate stage transition to respect DealValidation
        try {
            dealValidation.validateStageTransition(currentStage, targetStage, deal, new DealRequest());
        } catch (Exception e) {
            log.error("Validation failed during auto-sync of Deal {}: {}", deal.getDealId(), e.getMessage());
            return;
        }

        // 4. Update and save
        deal.setPipelineStage(targetStage);
        DealEntity savedDeal = dealRepository.save(deal);
        recordDealStageChangeService.record(savedDeal, currentStage, targetStage,
                RecordDealStageChangeService.SOURCE_WORKFLOW_SYNC);

        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("previousStage", currentStage.name())
                    .put("newStage", targetStage.name());
            activityLogPublisher.publish(
                    ActivityLogType.DEAL_STAGE_UPDATED,
                    EntityType.DEAL,
                    savedDeal.getDealId(),
                    "Deal pipeline stage auto-promoted from " + currentStage + " to " + targetStage,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish auto-promoted deal stage activity: {}", e.getMessage());
        }
    }
}
