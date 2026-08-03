package com.novax.leadora.unit.deal;
import com.novax.leadora.application.usecase.deal.*;

import com.novax.leadora.api.dto.response.DealWorkflowSummaryResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetDealWorkflowSummaryUseCaseTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private DealWorkflowResolver dealWorkflowResolver;

    @Mock
    private DealWorkflowSyncService dealWorkflowSyncService;

    @InjectMocks
    private GetDealWorkflowSummaryUseCase getDealWorkflowSummaryUseCase;

    @Test
    void execute_dealNotFound_throwsNotFoundException() {
        // Arrange
        UUID dealId = UUID.randomUUID();
        when(dealRepository.findById(dealId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> getDealWorkflowSummaryUseCase.execute(dealId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEAL_NOT_FOUND");
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void execute_returnsWorkflowSummary() {
        // Arrange
        UUID dealId = UUID.randomUUID();
        DealEntity deal = new DealEntity();
        deal.setDealId(dealId);
        deal.setStatus(DealStatus.OPEN);
        deal.setPipelineStage(DealPipelineStage.QUOTATION_SENT);

        when(dealRepository.findById(dealId)).thenReturn(Optional.of(deal));

        QuotationEntity activeQuotation = new QuotationEntity();
        activeQuotation.setQuotationId(UUID.randomUUID());
        activeQuotation.setStatus(com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.SENT);

        when(dealWorkflowResolver.resolveActiveQuotation(dealId)).thenReturn(Optional.of(activeQuotation));

        BookingEntity activeBooking = new BookingEntity();
        activeBooking.setBookingId(UUID.randomUUID());
        activeBooking.setStatus(com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus.CONFIRMED);

        when(dealWorkflowResolver.resolveActiveBooking(activeQuotation.getQuotationId()))
                .thenReturn(Optional.of(activeBooking));

        when(dealWorkflowResolver.resolveCurrentPaymentStatus(activeBooking.getBookingId()))
                .thenReturn(PaymentStatus.PAID);

        when(dealWorkflowResolver.hasPaidPaymentForActiveBooking(dealId)).thenReturn(true);

        // Act
        DealWorkflowSummaryResponse summary = getDealWorkflowSummaryUseCase.execute(dealId);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.getDealId()).isEqualTo(dealId);
        assertThat(summary.getDealStatus()).isEqualTo("OPEN");
        assertThat(summary.getPipelineStage()).isEqualTo("QUOTATION_SENT");
        assertThat(summary.getActiveQuotationId()).isEqualTo(activeQuotation.getQuotationId());
        assertThat(summary.getActiveQuotationStatus()).isEqualTo("SENT");
        assertThat(summary.getActiveBookingId()).isEqualTo(activeBooking.getBookingId());
        assertThat(summary.getActiveBookingStatus()).isEqualTo("CONFIRMED");
        assertThat(summary.getCurrentPaymentStatus()).isEqualTo("PAID");
        assertThat(summary.isHasPaidPayment()).isTrue();
    }
}
