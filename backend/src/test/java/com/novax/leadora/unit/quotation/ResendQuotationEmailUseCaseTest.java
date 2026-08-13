package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.quotation.QuotationAccessPolicy;
import com.novax.leadora.application.usecase.quotation.QuotationEmailService;
import com.novax.leadora.application.usecase.quotation.ResendQuotationEmailUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.config.QuotationProperties;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationSendLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendQuotationEmailUseCaseTest {

    @Mock private QuotationRepository quotationRepository;
    @Mock private QuotationSendLogRepository sendLogRepository;
    @Mock private QuotationEmailService quotationEmailService;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private QuotationAccessPolicy quotationAccessPolicy;
    @Mock private SystemAuditLogService systemAuditLogService;
    @Mock private ActivityLogPublisher activityLogPublisher;
    @Mock private QuotationConfirmationTokenRepository tokenRepository;
    @Mock private QuotationProperties quotationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResendQuotationEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResendQuotationEmailUseCase(
                quotationRepository,
                sendLogRepository,
                quotationEmailService,
                currentUserProvider,
                quotationAccessPolicy,
                systemAuditLogService,
                activityLogPublisher,
                objectMapper,
                tokenRepository,
                quotationProperties
        );
    }

    @Test
    @DisplayName("UT-Q-RESEND-01: Successfully resend email, invalidating old token and generating new one")
    void testResendSuccess() {
        UUID quotationId = UUID.randomUUID();
        UserEntity actor = UserEntity.builder().fullName("John Sales").build();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .version(1)
                .build();

        QuotationConfirmationTokenEntity tokenEntity = QuotationConfirmationTokenEntity.builder()
                .quotationId(quotationId)
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .openedAt(null) // NOT opened
                .build();

        QuotationSendLogEntity lastLog = QuotationSendLogEntity.builder()
                .quotation(quotation)
                .sendMethod("EMAIL")
                .recipientName("Jane Doe")
                .recipientEmail("jane@example.com")
                .recipientPhone("1234567")
                .personalMessage("Check this out")
                .build();

        List<QuotationSendLogEntity> sendLogs = new ArrayList<>();
        sendLogs.add(lastLog);

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(tokenRepository.findByQuotationId(quotationId)).thenReturn(Optional.of(tokenEntity));
        when(sendLogRepository.findByQuotation_QuotationIdOrderByCreatedAtDesc(quotationId)).thenReturn(sendLogs);
        when(currentUserProvider.resolve(null)).thenReturn(actor);
        when(quotationProperties.getPortalBaseUrl()).thenReturn("http://localhost:3000");
        when(quotationRepository.save(any(QuotationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QuotationResponse response = useCase.execute(quotationId);

        assertNotNull(response);
        verify(quotationAccessPolicy, times(1)).assertCanView(any(), eq(quotation));
        verify(tokenRepository, times(1)).deleteByQuotationId(quotationId);
        verify(tokenRepository, times(1)).save(any(QuotationConfirmationTokenEntity.class));
        
        // The resend goes back to the address on the previous send log, not to whatever the
        // customer record says now: the recipient is holding a link that this call replaces.
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(quotationEmailService, times(1)).sendQuotationEmail(
                eq(quotation), emailCaptor.capture(), nameCaptor.capture(), any(),
                eq("John Sales"), anyString());

        assertEquals("jane@example.com", emailCaptor.getValue());
        assertEquals("Jane Doe", nameCaptor.getValue());

        verify(sendLogRepository, times(1)).save(any(QuotationSendLogEntity.class));
        verify(systemAuditLogService, times(1)).log(eq("QUOTATION"), eq("QUOTATION"), eq(quotationId), eq("RESENT"), eq(actor), any(), any(), any());
    }

    @Test
    @DisplayName("UT-Q-RESEND-02: Fails to resend if quotation status is not PENDING_CUSTOMER_RESPONSE")
    void testResendWrongStatus() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.APPROVED) // approved, not sent yet or accepted
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.execute(quotationId));
        assertEquals("INVALID_STATUS_FOR_RESEND", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("UT-Q-RESEND-03: Fails to resend if the old email has already been opened")
    void testResendAlreadyOpened() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .build();

        QuotationConfirmationTokenEntity tokenEntity = QuotationConfirmationTokenEntity.builder()
                .quotationId(quotationId)
                .openedAt(OffsetDateTime.now()) // ALREADY OPENED
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(tokenRepository.findByQuotationId(quotationId)).thenReturn(Optional.of(tokenEntity));

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.execute(quotationId));
        assertEquals("LINK_ALREADY_OPENED", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("UT-Q-RESEND-04: Fails to resend if token is missing")
    void testResendTokenMissing() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.PENDING_CUSTOMER_RESPONSE)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(tokenRepository.findByQuotationId(quotationId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.execute(quotationId));
        assertEquals("TOKEN_NOT_FOUND", ex.getErrorCode());
    }
}
