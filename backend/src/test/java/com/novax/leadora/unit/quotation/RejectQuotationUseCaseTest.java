package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.RejectQuotationRequest;
import com.novax.leadora.application.usecase.quotation.RejectQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.event.QuotationRejectedEvent;
import com.novax.leadora.common.exception.BusinessException;
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
class RejectQuotationUseCaseTest {

        @Mock
        private QuotationRepository quotationRepository;

        @Mock
        private QuotationAcceptanceLogRepository acceptanceLogRepository;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private RejectQuotationUseCase rejectQuotationUseCase;

        @Test
        @DisplayName("UT-REJECT-01: Successful rejection updates status to REJECTED and saves log")
        void testRejectQuotationSuccess() {
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
                when(quotationRepository.save(any(QuotationEntity.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                RejectQuotationRequest request = new RejectQuotationRequest();
                request.setReason("Price too high.");

                rejectQuotationUseCase.execute(token, request);

                assertEquals(QuotationStatus.REJECTED, quotation.getStatus());
                verify(quotationRepository, times(1)).consumeToken(eq(token), any(OffsetDateTime.class));
                verify(quotationRepository, times(1)).save(quotation);
                verify(acceptanceLogRepository, times(1)).save(any());

                ArgumentCaptor<QuotationRejectedEvent> eventCaptor = ArgumentCaptor
                                .forClass(QuotationRejectedEvent.class);
                verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
                assertEquals(quotation, eventCaptor.getValue().getQuotation());
                assertEquals("Price too high.", eventCaptor.getValue().getReason());
        }

        @Test
        @DisplayName("UT-REJECT-02: Already used token throws TOKEN_ALREADY_USED")
        void testRejectQuotationUsedTokenThrowsException() {
                String token = UUID.randomUUID().toString();

                QuotationEntity quotation = QuotationEntity.builder()
                                .status(QuotationStatus.SENT)
                                .acceptanceToken(token)
                                .tokenExpiry(OffsetDateTime.now().plusDays(1))
                                .tokenUsed(true)
                                .build();

                when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));

                RejectQuotationRequest request = new RejectQuotationRequest();

                BusinessException ex = assertThrows(BusinessException.class,
                                () -> rejectQuotationUseCase.execute(token, request));
                assertEquals("TOKEN_ALREADY_USED", ex.getErrorCode());
                verify(quotationRepository, never()).consumeToken(any(), any());
        }

        @Test
        @DisplayName("UT-REJECT-03: Expired token throws TOKEN_EXPIRED")
        void testRejectQuotationExpiredTokenThrowsException() {
                String token = UUID.randomUUID().toString();

                QuotationEntity quotation = QuotationEntity.builder()
                                .status(QuotationStatus.SENT)
                                .acceptanceToken(token)
                                .tokenExpiry(OffsetDateTime.now().minusMinutes(5))
                                .tokenUsed(false)
                                .build();

                when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));

                RejectQuotationRequest request = new RejectQuotationRequest();

                BusinessException ex = assertThrows(BusinessException.class,
                                () -> rejectQuotationUseCase.execute(token, request));
                assertEquals("TOKEN_EXPIRED", ex.getErrorCode());
                verify(quotationRepository, never()).consumeToken(any(), any());
        }
}
