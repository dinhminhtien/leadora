package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.ProcessApprovalRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.ProcessQuotationApprovalUseCase;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationApprovalHistoryRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessQuotationApprovalUseCaseTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private QuotationApprovalHistoryRepository historyRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private SystemAuditLogService systemAuditLogService;

    @Mock
    private com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase startSlaTrackingUseCase;

    @Mock
    private com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher activityLogPublisher;

    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks
    private ProcessQuotationApprovalUseCase approvalUseCase;

    @BeforeEach
    void setUp() {
        UserEntity manager = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Manager Tran")
                .role(RoleEntity.builder().roleName("MANAGER").build())
                .build();
        lenient().when(currentUserProvider.resolve(null)).thenReturn(manager);
    }

    private ProcessApprovalRequest buildApprovalRequest(String action) {
        ProcessApprovalRequest req = new ProcessApprovalRequest();
        req.setAction(action);
        req.setNotes("Approved for VIP client");
        return req;
    }

    @Test
    @DisplayName("UT-APPROVAL-01: APPROVE action → status set to APPROVED")
    void testApproveAction() {
        UUID qId = UUID.randomUUID();
        UserEntity creator = UserEntity.builder().userId(UUID.randomUUID()).build();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(qId)
                .status(QuotationStatus.PENDING_APPROVAL)
                .createdBy(creator)
                .build();

        when(quotationRepository.findById(qId)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuotationResponse response = approvalUseCase.execute(qId, buildApprovalRequest("APPROVE"));

        assertNotNull(response);
        assertEquals(QuotationStatus.APPROVED, quotation.getStatus());
        verify(historyRepository).save(any());
        verify(notificationRepository).save(any());
    }

    @Test
    @DisplayName("UT-APPROVAL-02: REJECT action → status set to REJECTED")
    void testRejectAction() {
        UUID qId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(qId)
                .status(QuotationStatus.PENDING_APPROVAL)
                .createdBy(UserEntity.builder().userId(UUID.randomUUID()).build())
                .build();

        when(quotationRepository.findById(qId)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalUseCase.execute(qId, buildApprovalRequest("REJECT"));

        assertEquals(QuotationStatus.REJECTED, quotation.getStatus());
    }

    @Test
    @DisplayName("UT-APPROVAL-03: REQUEST_CHANGES action → status set to PENDING_REVISION")
    void testRequestChangesAction() {
        UUID qId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(qId)
                .status(QuotationStatus.PENDING_APPROVAL)
                .createdBy(UserEntity.builder().userId(UUID.randomUUID()).build())
                .build();

        when(quotationRepository.findById(qId)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalUseCase.execute(qId, buildApprovalRequest("REQUEST_CHANGES"));

        assertEquals(QuotationStatus.PENDING_REVISION, quotation.getStatus());
    }

    @Test
    @DisplayName("UT-APPROVAL-04: Non-PENDING_APPROVAL quotation → throws IllegalStateException")
    void testNonPendingQuotationThrows() {
        UUID qId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(qId)
                .status(QuotationStatus.APPROVED)
                .build();

        when(quotationRepository.findById(qId)).thenReturn(Optional.of(quotation));

        assertThrows(IllegalStateException.class,
                () -> approvalUseCase.execute(qId, buildApprovalRequest("APPROVE")));
    }

    @Test
    @DisplayName("UT-APPROVAL-05: Invalid action → throws IllegalArgumentException")
    void testInvalidActionThrows() {
        UUID qId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(qId)
                .status(QuotationStatus.PENDING_APPROVAL)
                .build();

        when(quotationRepository.findById(qId)).thenReturn(Optional.of(quotation));

        assertThrows(IllegalArgumentException.class,
                () -> approvalUseCase.execute(qId, buildApprovalRequest("INVALID")));
    }
}
