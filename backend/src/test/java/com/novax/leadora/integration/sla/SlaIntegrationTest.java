package com.novax.leadora.integration.sla;

import com.novax.leadora.application.usecase.sla.ProcessSlaBreachUseCase;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaIntegrationTest {

    @Mock
    private SlaTrackingRepository slaTrackingRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OpHandoverRepository opHandoverRepository;

    @InjectMocks
    private ProcessSlaBreachUseCase processSlaBreachUseCase;

    @Test
    @DisplayName("IT-SLA-01: Process SLA breach → ACTIVE records past deadline → marked BREACHED")
    void testProcessBreachMarksBreached() {
        SlaTrackingEntity tracking = SlaTrackingEntity.builder()
                .trackingId(UUID.randomUUID())
                .status(SlaStatus.ACTIVE)
                .deadlineAt(OffsetDateTime.now().minusHours(2))
                .activityType("LEAD_RESPONSE")
                .entityType("LEAD")
                .entityId(UUID.randomUUID())
                .build();

        RoleEntity managerRole = RoleEntity.builder().roleName("MANAGER").build();
        UserEntity manager = UserEntity.builder()
                .userId(UUID.randomUUID())
                .role(managerRole)
                .build();

        when(slaTrackingRepository.findByStatusAndDeadlineAtBefore(eq(SlaStatus.ACTIVE), any()))
                .thenReturn(List.of(tracking));
        when(userRepository.findAllWithRole()).thenReturn(List.of(manager));

        int result = processSlaBreachUseCase.execute();

        assertEquals(1, result);
        assertEquals(SlaStatus.BREACHED, tracking.getStatus());
        verify(notificationRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("IT-SLA-02: No breached records → returns 0")
    void testNoBreachReturnsZero() {
        when(slaTrackingRepository.findByStatusAndDeadlineAtBefore(eq(SlaStatus.ACTIVE), any()))
                .thenReturn(Collections.emptyList());

        int result = processSlaBreachUseCase.execute();

        assertEquals(0, result);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("IT-SLA-03: Multiple breaches → all marked BREACHED and notified")
    void testMultipleBreaches() {
        SlaTrackingEntity t1 = SlaTrackingEntity.builder()
                .trackingId(UUID.randomUUID())
                .status(SlaStatus.ACTIVE)
                .deadlineAt(OffsetDateTime.now().minusHours(1))
                .activityType("LEAD_RESPONSE")
                .entityType("LEAD")
                .entityId(UUID.randomUUID())
                .build();
        SlaTrackingEntity t2 = SlaTrackingEntity.builder()
                .trackingId(UUID.randomUUID())
                .status(SlaStatus.ACTIVE)
                .deadlineAt(OffsetDateTime.now().minusHours(5))
                .activityType("FOLLOW_UP_TASK")
                .entityType("TASK")
                .entityId(UUID.randomUUID())
                .build();

        RoleEntity adminRole = RoleEntity.builder().roleName("ADMIN").build();
        UserEntity admin = UserEntity.builder()
                .userId(UUID.randomUUID())
                .role(adminRole)
                .build();

        when(slaTrackingRepository.findByStatusAndDeadlineAtBefore(eq(SlaStatus.ACTIVE), any()))
                .thenReturn(List.of(t1, t2));
        when(userRepository.findAllWithRole()).thenReturn(List.of(admin));

        int result = processSlaBreachUseCase.execute();

        assertEquals(2, result);
        assertEquals(SlaStatus.BREACHED, t1.getStatus());
        assertEquals(SlaStatus.BREACHED, t2.getStatus());
        verify(slaTrackingRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("IT-SLA-04: Breach notification sent to managers + assigned LEAD user")
    void testBreachNotificationToManagerAndAssigned() {
        UUID leadEntityId = UUID.randomUUID();
        SlaTrackingEntity tracking = SlaTrackingEntity.builder()
                .trackingId(UUID.randomUUID())
                .status(SlaStatus.ACTIVE)
                .deadlineAt(OffsetDateTime.now().minusHours(1))
                .activityType("LEAD_RESPONSE")
                .entityType("LEAD")
                .entityId(leadEntityId)
                .build();

        RoleEntity managerRole = RoleEntity.builder().roleName("MANAGER").build();
        UserEntity manager = UserEntity.builder()
                .userId(UUID.randomUUID())
                .role(managerRole)
                .build();

        UserEntity assignedStaff = UserEntity.builder()
                .userId(UUID.randomUUID())
                .build();

        when(slaTrackingRepository.findByStatusAndDeadlineAtBefore(eq(SlaStatus.ACTIVE), any()))
                .thenReturn(List.of(tracking));
        when(userRepository.findAllWithRole()).thenReturn(List.of(manager));
        when(leadRepository.findById(leadEntityId)).thenReturn(java.util.Optional.of(
                com.novax.leadora.infrastructure.persistence.entity.LeadEntity.builder()
                        .assignedUser(assignedStaff)
                        .build()));

        processSlaBreachUseCase.execute();

        // Manager + assigned staff = 2 notifications
        verify(notificationRepository, times(2)).save(any());
    }
}
