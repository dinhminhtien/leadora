package com.novax.leadora.integration.quotation;

import com.novax.leadora.api.dto.request.ExpireOverdueRequest;
import com.novax.leadora.application.usecase.quotation.ExpireOverdueQuotationsUseCase;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationClosureLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationIntegrationTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private QuotationClosureLogRepository closureLogRepository;

    @InjectMocks
    private ExpireOverdueQuotationsUseCase expireUseCase;

    @Test
    @DisplayName("IT-QUOT-01: Overdue quotations → expired and counted")
    void testExpireOverdueQuotations() {
        QuotationEntity overdueQ = QuotationEntity.builder()
                .quotationId(UUID.randomUUID())
                .status(QuotationStatus.SENT)
                .validUntil(LocalDate.now().minusDays(5))
                .build();

        when(quotationRepository.findByStatusInAndValidUntilBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(overdueQ));
        when(quotationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = expireUseCase.execute(new ExpireOverdueRequest());

        assertEquals(1, result.get("expiredCount"));
        assertEquals(QuotationStatus.EXPIRED, overdueQ.getStatus());
        verify(closureLogRepository).save(any());
    }

    @Test
    @DisplayName("IT-QUOT-02: No overdue quotations → returns 0 count")
    void testNoOverdueQuotations() {
        when(quotationRepository.findByStatusInAndValidUntilBefore(anyList(), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = expireUseCase.execute(new ExpireOverdueRequest());

        assertEquals(0, result.get("expiredCount"));
        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("IT-QUOT-03: Multiple overdue quotations → all expired with log entries")
    void testMultipleOverdueQuotations() {
        QuotationEntity q1 = QuotationEntity.builder()
                .quotationId(UUID.randomUUID())
                .status(QuotationStatus.DRAFT)
                .validUntil(LocalDate.now().minusDays(1))
                .build();
        QuotationEntity q2 = QuotationEntity.builder()
                .quotationId(UUID.randomUUID())
                .status(QuotationStatus.PENDING_APPROVAL)
                .validUntil(LocalDate.now().minusDays(3))
                .build();

        when(quotationRepository.findByStatusInAndValidUntilBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(q1, q2));
        when(quotationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = expireUseCase.execute(new ExpireOverdueRequest());

        assertEquals(2, result.get("expiredCount"));
        assertEquals(QuotationStatus.EXPIRED, q1.getStatus());
        assertEquals(QuotationStatus.EXPIRED, q2.getStatus());
        verify(closureLogRepository, times(2)).save(any());
    }
}
