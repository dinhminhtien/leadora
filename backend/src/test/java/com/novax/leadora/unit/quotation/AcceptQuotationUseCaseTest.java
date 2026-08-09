package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.AcceptQuotationRequest;
import com.novax.leadora.application.usecase.quotation.AcceptQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.ConvertToBookingUseCase;
import com.novax.leadora.application.usecase.quotation.event.QuotationAcceptedEvent;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationAcceptanceLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcceptQuotationUseCaseTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private ConvertToBookingUseCase convertToBookingUseCase;

    @Mock
    private QuotationAcceptanceLogRepository acceptanceLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AcceptQuotationUseCase acceptQuotationUseCase;

    @Test
    @DisplayName("UT-ACCEPT-01: Successful acceptance conversions")
    void testAcceptQuotationSuccess() {
        UUID quotationId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .acceptanceToken(token)
                .tokenExpiry(OffsetDateTime.now().plusDays(1))
                .tokenUsed(false)
                .build();

        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));
        when(quotationRepository.consumeToken(eq(token), any(OffsetDateTime.class))).thenReturn(1);
        when(quotationRepository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AcceptQuotationRequest request = new AcceptQuotationRequest();
        request.setNotes("Confirm my reservation please.");

        acceptQuotationUseCase.execute(token, request);

        assertEquals(QuotationStatus.ACCEPTED, quotation.getStatus());
        verify(quotationRepository, times(1)).consumeToken(eq(token), any(OffsetDateTime.class));
        verify(quotationRepository, times(1)).save(quotation);
        verify(convertToBookingUseCase, times(1)).execute(eq(quotationId), any(), eq(true));
        verify(acceptanceLogRepository, times(1)).save(any());

        ArgumentCaptor<QuotationAcceptedEvent> eventCaptor = ArgumentCaptor.forClass(QuotationAcceptedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertEquals(quotation, eventCaptor.getValue().getQuotation());
    }

    @Test
    @DisplayName("UT-ACCEPT-02: Already used token throws TOKEN_ALREADY_USED")
    void testAcceptQuotationUsedTokenThrowsException() {
        String token = UUID.randomUUID().toString();

        QuotationEntity quotation = QuotationEntity.builder()
                .status(QuotationStatus.SENT)
                .acceptanceToken(token)
                .tokenExpiry(OffsetDateTime.now().plusDays(1))
                .tokenUsed(true)
                .build();

        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));

        AcceptQuotationRequest request = new AcceptQuotationRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> acceptQuotationUseCase.execute(token, request));
        assertEquals("TOKEN_ALREADY_USED", ex.getErrorCode());
        verify(quotationRepository, never()).consumeToken(any(), any());
        verify(convertToBookingUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("UT-ACCEPT-03: Expired token throws TOKEN_EXPIRED")
    void testAcceptQuotationExpiredTokenThrowsException() {
        String token = UUID.randomUUID().toString();

        QuotationEntity quotation = QuotationEntity.builder()
                .status(QuotationStatus.SENT)
                .acceptanceToken(token)
                .tokenExpiry(OffsetDateTime.now().minusMinutes(5))
                .tokenUsed(false)
                .build();

        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));

        AcceptQuotationRequest request = new AcceptQuotationRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> acceptQuotationUseCase.execute(token, request));
        assertEquals("TOKEN_EXPIRED", ex.getErrorCode());
        verify(quotationRepository, never()).consumeToken(any(), any());
        verify(convertToBookingUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("UT-ACCEPT-04: Non-existent token throws ResourceNotFoundException")
    void testAcceptQuotationNonExistentTokenThrowsException() {
        String token = "invalid-token";
        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.empty());

        AcceptQuotationRequest request = new AcceptQuotationRequest();

        assertThrows(ResourceNotFoundException.class,
                () -> acceptQuotationUseCase.execute(token, request));
    }

    @Test
    @DisplayName("UT-ACCEPT-05: Incompatible quotation status throws QUOTATION_INVALID_STATUS")
    void testAcceptQuotationInvalidStatusThrowsException() {
        String token = UUID.randomUUID().toString();

        QuotationEntity quotation = QuotationEntity.builder()
                .status(QuotationStatus.DRAFT)
                .acceptanceToken(token)
                .tokenExpiry(OffsetDateTime.now().plusDays(1))
                .tokenUsed(false)
                .build();

        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));

        AcceptQuotationRequest request = new AcceptQuotationRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> acceptQuotationUseCase.execute(token, request));
        assertEquals("QUOTATION_INVALID_STATUS", ex.getErrorCode());
        verify(quotationRepository, never()).consumeToken(any(), any());
    }
}
