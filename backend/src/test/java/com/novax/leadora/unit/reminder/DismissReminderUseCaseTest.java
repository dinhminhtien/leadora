package com.novax.leadora.unit.reminder;

import com.novax.leadora.application.usecase.reminder.*;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DismissReminderUseCaseTest {

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private DismissReminderUseCase dismissReminderUseCase;

    @BeforeEach
    void setUp() {
        dismissReminderUseCase = new DismissReminderUseCase(
                reminderRepository,
                userRepository,
                currentUserProvider
        );
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
                .build();
    }

    // ==========================================
    // NORMAL (N) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-DISMISS-N01: Assignee can dismiss a reminder")
    void execute_dismissAsAssignee_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));

        dismissReminderUseCase.execute(reminderId);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DONE);
        verify(reminderRepository).save(reminder);
    }

    @Test
    @DisplayName("UT-REM-DISMISS-N02: Manager can dismiss reminder of another user")
    void execute_dismissAsManager_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "SALES");
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(manager);
        when(userRepository.findWithRoleByUserId(managerId)).thenReturn(Optional.of(manager));

        dismissReminderUseCase.execute(reminderId);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DONE);
        verify(reminderRepository).save(reminder);
    }

    // ==========================================
    // ABNORMAL (A) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-DISMISS-A01: Non-assignee, non-manager caller triggers BusinessException with FORBIDDEN")
    void execute_dismissUnauthorized_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "SALES");
        UUID otherId = UUID.randomUUID();
        UserEntity otherUser = createTestUser(otherId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(otherUser);
        when(userRepository.findWithRoleByUserId(otherId)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> dismissReminderUseCase.execute(reminderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED_DISMISS");

        verify(reminderRepository, never()).save(any(ReminderEntity.class));
    }

    @Test
    @DisplayName("UT-REM-DISMISS-A02: Dismissing already completed (DONE) reminder triggers CONFLICT")
    void execute_dismissAlreadyDone_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.DONE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));

        assertThatThrownBy(() -> dismissReminderUseCase.execute(reminderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("errorCode", "REMINDER_ALREADY_DONE");

        verify(reminderRepository, never()).save(any(ReminderEntity.class));
    }

    // ==========================================
    // BOUNDARY (B) / EDGE CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-DISMISS-B01: Dismissing non-existent reminder triggers ResourceNotFoundException")
    void execute_dismissNonExistentReminder_throwsResourceNotFoundException() {
        UUID reminderId = UUID.randomUUID();
        when(reminderRepository.findById(reminderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dismissReminderUseCase.execute(reminderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reminder")
                .hasMessageContaining(reminderId.toString());

        verify(reminderRepository, never()).save(any(ReminderEntity.class));
    }

    @Test
    @DisplayName("UT-REM-DISMISS-B02: Dismissing when caller user role fetch fails triggers ResourceNotFoundException")
    void execute_dismissNonExistentCallerUser_throwsResourceNotFoundException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dismissReminderUseCase.execute(reminderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining(callerId.toString());

        verify(reminderRepository, never()).save(any(ReminderEntity.class));
    }
}
