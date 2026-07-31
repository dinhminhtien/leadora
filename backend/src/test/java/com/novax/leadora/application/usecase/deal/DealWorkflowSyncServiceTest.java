package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealWorkflowSyncServiceTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private DealWorkflowResolver dealWorkflowResolver;

    @Mock
    private DealValidation dealValidation;

    @Mock
    private ActivityLogPublisher activityLogPublisher;

    // Records the stage transition in the same transaction as the change itself
    // (RecordDealStageChangeService); mocked here because these tests assert on the deal,
    // not on the history row.
    @Mock
    private RecordDealStageChangeService recordDealStageChangeService;

    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks
    private DealWorkflowSyncService dealWorkflowSyncService;

    private UUID dealId;
    private DealEntity deal;

    @BeforeEach
    void setUp() {
        dealId = UUID.randomUUID();
        deal = new DealEntity();
        deal.setDealId(dealId);
        deal.setStatus(DealStatus.OPEN);
        deal.setPipelineStage(DealPipelineStage.INQUIRY);
    }

    @Test
    @DisplayName("UT-DEAL-SYNC-01: No promotion needed when no quotation or booking exists")
    void testNoPromotionNeeded() {
        when(dealWorkflowResolver.resolveActiveQuotation(dealId)).thenReturn(Optional.empty());

        dealWorkflowSyncService.syncPipelineStage(deal);

        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-DEAL-SYNC-02: Quotation present promotes to QUOTATION_SENT")
    void testPromotionToQuotationSent() {
        QuotationEntity activeQuot = new QuotationEntity();
        activeQuot.setQuotationId(UUID.randomUUID());
        activeQuot.setStatus(QuotationStatus.SENT);
        activeQuot.setTotalAmount(BigDecimal.valueOf(50000000));

        when(dealWorkflowResolver.resolveActiveQuotation(dealId)).thenReturn(Optional.of(activeQuot));
        when(dealWorkflowResolver.resolveActiveBooking(activeQuot.getQuotationId())).thenReturn(Optional.empty());
        when(dealWorkflowResolver.getStageOrder(DealPipelineStage.INQUIRY)).thenReturn(0);
        when(dealWorkflowResolver.getStageOrder(DealPipelineStage.QUOTATION_SENT)).thenReturn(2);

        dealWorkflowSyncService.syncPipelineStage(deal);

        verify(dealRepository).save(argThat(d -> d.getPipelineStage() == DealPipelineStage.QUOTATION_SENT
                && d.getExpectedRevenue().compareTo(BigDecimal.valueOf(50000000)) == 0));
    }

    @Test
    @DisplayName("UT-DEAL-SYNC-03: Active booking promotes to PENDING_CONFIRMATION")
    void testPromotionToPendingConfirmation() {
        QuotationEntity activeQuot = new QuotationEntity();
        activeQuot.setQuotationId(UUID.randomUUID());

        BookingEntity activeBooking = new BookingEntity();
        activeBooking.setBookingId(UUID.randomUUID());
        activeBooking.setStatus(BookingStatus.PENDING);

        when(dealWorkflowResolver.resolveActiveQuotation(dealId)).thenReturn(Optional.of(activeQuot));
        when(dealWorkflowResolver.resolveActiveBooking(activeQuot.getQuotationId()))
                .thenReturn(Optional.of(activeBooking));
        when(dealWorkflowResolver.getStageOrder(DealPipelineStage.INQUIRY)).thenReturn(0);
        when(dealWorkflowResolver.getStageOrder(DealPipelineStage.PENDING_CONFIRMATION)).thenReturn(4);

        dealWorkflowSyncService.syncPipelineStage(deal);

        verify(dealRepository).save(argThat(d -> d.getPipelineStage() == DealPipelineStage.PENDING_CONFIRMATION
                && d.getNotes() != null
                && d.getExpectedCloseDate() != null));
    }

    @Test
    @DisplayName("UT-DEAL-SYNC-04: Confirmed booking promotes to BOOKING_CONFIRMED")
    void testPromotionToBookingConfirmed() {
        QuotationEntity activeQuot = new QuotationEntity();
        activeQuot.setQuotationId(UUID.randomUUID());

        BookingEntity activeBooking = new BookingEntity();
        activeBooking.setBookingId(UUID.randomUUID());
        activeBooking.setStatus(BookingStatus.CONFIRMED);

        when(dealWorkflowResolver.resolveActiveQuotation(dealId)).thenReturn(Optional.of(activeQuot));
        when(dealWorkflowResolver.resolveActiveBooking(activeQuot.getQuotationId()))
                .thenReturn(Optional.of(activeBooking));
        when(dealWorkflowResolver.getStageOrder(DealPipelineStage.INQUIRY)).thenReturn(0);
        when(dealWorkflowResolver.getStageOrder(DealPipelineStage.BOOKING_CONFIRMED)).thenReturn(5);

        dealWorkflowSyncService.syncPipelineStage(deal);

        verify(dealRepository).save(argThat(d -> d.getPipelineStage() == DealPipelineStage.BOOKING_CONFIRMED));
    }
}
