package com.novax.leadora.unit.reminder;

import com.novax.leadora.application.usecase.reminder.*;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalateReminderUseCaseTest {

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private SystemAuditLogService systemAuditLogService;

    private EscalateReminderUseCase escalateReminderUseCase;

    @BeforeEach
    void setUp() {
        escalateReminderUseCase = new EscalateReminderUseCase(
                reminderRepository,
                userRepository,
                notificationRepository,
                currentUserProvider,
                systemAuditLogService);
    }

    private UserEntity createTestUser(UUID id, String roleName) {
        RoleEntity role = RoleEntity.builder().roleName(roleName).build();
        return UserEntity.builder()
                .userId(id)
                .fullName("Test User")
                .role(role)
                .build();
    }

    private ReminderEntity createTestReminder(UUID reminderId, UserEntity assignee, ReminderStatus status) {
        return ReminderEntity.builder()
                .reminderId(reminderId)
                .user(assignee)
                .status(status)
                .title("Overdue follow up")
                .build();
    }

    // ==========================================
    // NORMAL (N) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-ESCALATE-N01: Assignee can escalate an OVERDUE reminder to managers")
    void execute_escalateAsAssignee_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.OVERDUE);

        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));
        when(userRepository.findByRoleName("MANAGER")).thenReturn(List.of(manager));

        escalateReminderUseCase.execute(reminderId);

        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(systemAuditLogService).log(eq("REMINDER"), eq("REMINDER"), eq(reminderId), eq("ESCALATED"), eq(assignee),
                any(), any(), eq("Escalated to 1 manager(s)"));
    }

    @Test
    @DisplayName("UT-REM-ESCALATE-N02: Manager can escalate an OVERDUE reminder of another user")
    void execute_escalateAsManager_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "SALES");
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.OVERDUE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(manager);
        when(userRepository.findWithRoleByUserId(managerId)).thenReturn(Optional.of(manager));
        when(userRepository.findByRoleName("MANAGER")).thenReturn(List.of(manager));

        escalateReminderUseCase.execute(reminderId);

        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }

    // ==========================================
    // ABNORMAL (A) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-ESCALATE-A01: Non-assignee, non-manager caller throws BusinessException with FORBIDDEN")
    void execute_escalateUnauthorized_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "SALES");
        UUID otherId = UUID.randomUUID();
        UserEntity otherUser = createTestUser(otherId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.OVERDUE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(otherUser);
        when(userRepository.findWithRoleByUserId(otherId)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> escalateReminderUseCase.execute(reminderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED_ESCALATE");

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("UT-REM-ESCALATE-A02: Escalating non-overdue (PENDING) reminder triggers BusinessException with CONFLICT")
    void execute_escalateNotOverdue_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));

        assertThatThrownBy(() -> escalateReminderUseCase.execute(reminderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("errorCode", "NOT_OVERDUE");

        verifyNoInteractions(notificationRepository);
    }

    // ==========================================
    // BOUNDARY (B) / EDGE CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-ESCALATE-B01: Escalating already completed (DONE) reminder triggers BusinessException with CONFLICT")
    void execute_escalateAlreadyDone_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.DONE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));

        assertThatThrownBy(() -> escalateReminderUseCase.execute(reminderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("errorCode", "REMINDER_ALREADY_DONE");

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("UT-REM-ESCALATE-B02: Escalating with no managers in database executes successfully without sending notifications")
    void execute_escalateWithNoManagers_succeedsWithoutSendingNotifications() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.OVERDUE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));
        when(userRepository.findByRoleName("MANAGER")).thenReturn(Collections.emptyList());

        escalateReminderUseCase.execute(reminderId);

        verify(notificationRepository, never()).save(any(NotificationEntity.class));
        verify(systemAuditLogService).log(eq("REMINDER"), eq("REMINDER"), eq(reminderId), eq("ESCALATED"), eq(assignee),
                any(), any(), eq("Escalated to 0 manager(s)"));
    }
}
