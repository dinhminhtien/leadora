package com.novax.leadora.unit.reminder;

import com.novax.leadora.application.usecase.reminder.*;
import com.novax.leadora.api.dto.request.UpdateReminderRequest;
import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateReminderUseCaseTest {

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private SystemAuditLogService systemAuditLogService;

    private UpdateReminderUseCase updateReminderUseCase;

    @BeforeEach
    void setUp() {
        updateReminderUseCase = new UpdateReminderUseCase(
                reminderRepository,
                userRepository,
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
                .title("Original Title")
                .description("Original Desc")
                .status(status)
                .priority(ReminderPriority.MEDIUM)
                .remindAt(OffsetDateTime.now().plusDays(2))
                .build();
    }

    // ==========================================
    // NORMAL (N) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-UPDATE-N01: Assignee can update reminder fields")
    void execute_updateAsAssignee_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));
        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(reminder);

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setTitle("New Title");
        request.setDescription("New Desc");

        ReminderResponse response = updateReminderUseCase.execute(reminderId, request);

        assertThat(response).isNotNull();
        assertThat(reminder.getTitle()).isEqualTo("New Title");
        assertThat(reminder.getDescription()).isEqualTo("New Desc");
        verify(reminderRepository).save(reminder);
        verify(systemAuditLogService).log(eq("REMINDER"), eq("REMINDER"), eq(reminderId), eq("UPDATED"), eq(assignee),
                any(), any(), any());
    }

    @Test
    @DisplayName("UT-REM-UPDATE-N02: Manager can update reminder of another user")
    void execute_updateAsManager_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "SALES");
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(manager);
        when(userRepository.findWithRoleByUserId(managerId)).thenReturn(Optional.of(manager));
        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(reminder);

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setPriority("HIGH");

        ReminderResponse response = updateReminderUseCase.execute(reminderId, request);

        assertThat(response).isNotNull();
        assertThat(reminder.getPriority()).isEqualTo(ReminderPriority.HIGH);
    }

    // ==========================================
    // ABNORMAL (A) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-UPDATE-A01: Non-assignee, non-manager caller throws BusinessException with FORBIDDEN")
    void execute_updateUnauthorized_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "SALES");
        UUID otherUserId = UUID.randomUUID();
        UserEntity otherUser = createTestUser(otherUserId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(otherUser);
        when(userRepository.findWithRoleByUserId(otherUserId)).thenReturn(Optional.of(otherUser));

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setTitle("Unauthorized Edit");

        assertThatThrownBy(() -> updateReminderUseCase.execute(reminderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED_UPDATE");

        verify(reminderRepository, never()).save(any(ReminderEntity.class));
    }

    @Test
    @DisplayName("UT-REM-UPDATE-A02: Update already DONE reminder without forceIfDone throws CONFLICT")
    void execute_updateAlreadyDoneWithoutForce_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.DONE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setTitle("New Title");
        request.setForceIfDone(false);

        assertThatThrownBy(() -> updateReminderUseCase.execute(reminderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("errorCode", "REMINDER_ALREADY_DONE");
    }

    // ==========================================
    // BOUNDARY (B) / EDGE CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-UPDATE-B01: Update already DONE reminder with forceIfDone=true succeeds")
    void execute_updateAlreadyDoneWithForce_succeeds() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.DONE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));
        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(reminder);

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setTitle("Forced Title Update");
        request.setForceIfDone(true);

        ReminderResponse response = updateReminderUseCase.execute(reminderId, request);

        assertThat(response).isNotNull();
        assertThat(reminder.getTitle()).isEqualTo("Forced Title Update");
    }

    @Test
    @DisplayName("UT-REM-UPDATE-B02: Past deadline triggers BusinessException with BAD_REQUEST")
    void execute_updateInvalidDeadline_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setRemindAt(OffsetDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> updateReminderUseCase.execute(reminderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DEADLINE");
    }

    @Test
    @DisplayName("UT-REM-UPDATE-B03: Invalid priority triggers BusinessException with BAD_REQUEST")
    void execute_updateInvalidPriority_throwsBusinessException() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.PENDING);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setPriority("INVALID_LEVEL");

        assertThatThrownBy(() -> updateReminderUseCase.execute(reminderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_PRIORITY");
    }

    @Test
    @DisplayName("UT-REM-UPDATE-B04: Extending deadline on OVERDUE reminder resets status back to PENDING")
    void execute_updateOverdueResetsToPending() {
        UUID reminderId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UserEntity assignee = createTestUser(callerId, "SALES");
        ReminderEntity reminder = createTestReminder(reminderId, assignee, ReminderStatus.OVERDUE);

        when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(reminder));
        when(currentUserProvider.resolve(null)).thenReturn(assignee);
        when(userRepository.findWithRoleByUserId(callerId)).thenReturn(Optional.of(assignee));
        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(reminder);

        UpdateReminderRequest request = new UpdateReminderRequest();
        request.setRemindAt(OffsetDateTime.now().plusDays(5));

        ReminderResponse response = updateReminderUseCase.execute(reminderId, request);

        assertThat(response).isNotNull();
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(reminder.getRemindAt()).isEqualTo(request.getRemindAt());
    }
}
