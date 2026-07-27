package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealWorkflowSummaryResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetDealWorkflowSummaryUseCase {

    private final DealRepository dealRepository;
    private final DealWorkflowResolver dealWorkflowResolver;
    private final DealWorkflowSyncService dealWorkflowSyncService;

    @Transactional
    public DealWorkflowSummaryResponse execute(UUID dealId) {
        dealWorkflowSyncService.syncPipelineStage(dealId);

        DealEntity deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found with ID: " + dealId, HttpStatus.NOT_FOUND));

        QuotationEntity activeQuot = dealWorkflowResolver.resolveActiveQuotation(dealId).orElse(null);
        BookingEntity activeBooking = null;
        if (activeQuot != null) {
            activeBooking = dealWorkflowResolver.resolveActiveBooking(activeQuot.getQuotationId()).orElse(null);
        }

        PaymentStatus payStatus = null;
        boolean hasPaid = false;
        if (activeBooking != null) {
            payStatus = dealWorkflowResolver.resolveCurrentPaymentStatus(activeBooking.getBookingId());
            hasPaid = dealWorkflowResolver.hasPaidPaymentForActiveBooking(dealId);
        }

        return DealWorkflowSummaryResponse.builder()
                .dealId(deal.getDealId())
                .dealStatus(deal.getStatus() != null ? deal.getStatus().name() : null)
                .pipelineStage(deal.getPipelineStage() != null ? deal.getPipelineStage().name() : null)
                .activeQuotationId(activeQuot != null ? activeQuot.getQuotationId() : null)
                .activeQuotationStatus(activeQuot != null && activeQuot.getStatus() != null ? activeQuot.getStatus().name() : null)
                .activeBookingId(activeBooking != null ? activeBooking.getBookingId() : null)
                .activeBookingStatus(activeBooking != null && activeBooking.getStatus() != null ? activeBooking.getStatus().name() : null)
                .currentPaymentStatus(payStatus != null ? payStatus.name() : null)
                .hasPaidPayment(hasPaid)
                .build();
    }
}
