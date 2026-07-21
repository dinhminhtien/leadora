package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.SubmitQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.SubmitQuotationUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitQuotationUseCaseTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private QuotationDetailRepository quotationDetailRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private SubmitQuotationUseCase submitQuotationUseCase;

    @Test
    @DisplayName("UT-SUBMIT-01: Discount <= 10% auto-approves quotation")
    void testSubmitQuotationAutoApproved() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity draftQuotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .discountPercent(BigDecimal.valueOf(5)) // 5% discount <= 10% threshold
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(draftQuotation));
        when(quotationRepository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(Collections.emptyList());

        QuotationResponse response = submitQuotationUseCase.execute(quotationId, new SubmitQuotationRequest());

        assertNotNull(response);
        assertEquals("approved", response.getStatus());
        verify(quotationRepository, times(1)).save(draftQuotation);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("UT-SUBMIT-02: Discount > 10% sets PENDING_APPROVAL and notifies managers")
    void testSubmitQuotationRequiresManagerApproval() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity draftQuotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .discountPercent(BigDecimal.valueOf(15)) // 15% discount > 10% threshold
                .build();

        UserEntity manager = UserEntity.builder().userId(UUID.randomUUID()).email("manager@leadora.vn").build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(draftQuotation));
        when(quotationRepository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByRoleName("MANAGER")).thenReturn(List.of(manager));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(Collections.emptyList());

        QuotationResponse response = submitQuotationUseCase.execute(quotationId, new SubmitQuotationRequest());

        assertNotNull(response);
        assertEquals("pending_approval", response.getStatus());
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("UT-SUBMIT-03: Submitting non-DRAFT quotation throws IllegalStateException")
    void testSubmitNonDraftQuotationThrowsException() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity approvedQuotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.APPROVED)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(approvedQuotation));

        assertThrows(IllegalStateException.class, () -> submitQuotationUseCase.execute(quotationId, new SubmitQuotationRequest()));
        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SUBMIT-04: Submitting non-existent quotation throws ResourceNotFoundException")
    void testSubmitNonExistentQuotationThrowsException() {
        UUID id = UUID.randomUUID();
        when(quotationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submitQuotationUseCase.execute(id, new SubmitQuotationRequest()));
    }
}
