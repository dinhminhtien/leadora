package com.novax.leadora.unit.deal;
import com.novax.leadora.application.usecase.deal.*;

import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoWinDealByPaymentUseCaseTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private SystemAuditLogService auditLogService;

    @Mock
    private DealWorkflowResolver dealWorkflowResolver;

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
    private AutoWinDealByPaymentUseCase useCase;

    private UserEntity actor;
    private DealEntity deal;
    private QuotationEntity quotation;
    private BookingEntity booking;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        actor = new UserEntity();
        actor.setUserId(UUID.randomUUID());

        deal = new DealEntity();
        deal.setDealId(UUID.randomUUID());
        deal.setStatus(DealStatus.OPEN);
        deal.setPipelineStage(DealPipelineStage.NEGOTIATION);

        quotation = new QuotationEntity();
        quotation.setQuotationId(UUID.randomUUID());
        quotation.setDeal(deal);

        booking = new BookingEntity();
        booking.setBookingId(UUID.randomUUID());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setQuotation(quotation);
        booking.setBookingCode("B-12345");

        payment = new PaymentEntity();
        payment.setPaymentId(UUID.randomUUID());
        payment.setStatus(PaymentStatus.PAID);
        payment.setBooking(booking);
    }

    @Test
    void execute_successfulAutoWin() {
        // Arrange
        when(dealRepository.findByIdForUpdate(deal.getDealId())).thenReturn(Optional.of(deal));
        when(dealWorkflowResolver.resolveActiveQuotation(deal.getDealId())).thenReturn(Optional.of(quotation));
        when(dealWorkflowResolver.resolveActiveBooking(quotation.getQuotationId())).thenReturn(Optional.of(booking));

        // Act
        useCase.execute(payment, actor);

        // Assert
        assertThat(deal.getStatus()).isEqualTo(DealStatus.WON);
        assertThat(deal.getPipelineStage()).isEqualTo(DealPipelineStage.CLOSED_WON);
        verify(dealRepository).save(deal);
        verify(auditLogService).log(
                eq("DEAL"), eq("Deal"), eq(deal.getDealId()),
                eq("AUTO_CLOSED_WON"), eq(actor),
                eq("OPEN"), eq("WON"),
                anyString()
        );
    }

    @Test
    void execute_alreadyWon_doesNothing() {
        // Arrange
        deal.setStatus(DealStatus.WON);
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);

        // Act
        useCase.execute(payment, actor);

        // Assert
        verify(dealRepository, never()).findByIdForUpdate(any());
        verify(dealRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_dealIsLost_throwsConflictException() {
        // Arrange
        deal.setStatus(DealStatus.LOST);
        when(dealRepository.findByIdForUpdate(deal.getDealId())).thenReturn(Optional.of(deal));
        when(dealWorkflowResolver.resolveActiveQuotation(deal.getDealId())).thenReturn(Optional.of(quotation));
        when(dealWorkflowResolver.resolveActiveBooking(quotation.getQuotationId())).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(payment, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEAL_STATE_CONFLICT");
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(dealRepository, never()).save(any());
    }

    @Test
    void execute_paymentNotPaid_doesNothing() {
        // Arrange
        payment.setStatus(PaymentStatus.PENDING);

        // Act
        useCase.execute(payment, actor);

        // Assert
        verify(dealRepository, never()).findByIdForUpdate(any());
        verify(dealRepository, never()).save(any());
    }

    @Test
    void execute_bookingNotConfirmed_doesNothing() {
        // Arrange
        booking.setStatus(BookingStatus.PENDING);

        // Act
        useCase.execute(payment, actor);

        // Assert
        verify(dealRepository, never()).findByIdForUpdate(any());
        verify(dealRepository, never()).save(any());
    }

    @Test
    void execute_inactiveQuotation_throwsConflictException() {
        // Arrange
        when(dealRepository.findByIdForUpdate(deal.getDealId())).thenReturn(Optional.of(deal));
        when(dealWorkflowResolver.resolveActiveQuotation(deal.getDealId())).thenReturn(Optional.empty()); // Or different quotation

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(payment, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEAL_STATE_CONFLICT");
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(dealRepository, never()).save(any());
    }

    @Test
    void execute_inactiveBooking_throwsConflictException() {
        // Arrange
        when(dealRepository.findByIdForUpdate(deal.getDealId())).thenReturn(Optional.of(deal));
        when(dealWorkflowResolver.resolveActiveQuotation(deal.getDealId())).thenReturn(Optional.of(quotation));
        when(dealWorkflowResolver.resolveActiveBooking(quotation.getQuotationId())).thenReturn(Optional.empty()); // Or different booking

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(payment, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEAL_STATE_CONFLICT");
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(dealRepository, never()).save(any());
    }
}
