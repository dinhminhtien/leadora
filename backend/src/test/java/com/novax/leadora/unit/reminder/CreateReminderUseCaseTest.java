package com.novax.leadora.unit.reminder;

import com.novax.leadora.application.usecase.reminder.*;
import com.novax.leadora.api.dto.request.CreateReminderRequest;
import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateReminderUseCaseTest {

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

    private CreateReminderUseCase createReminderUseCase;

    @BeforeEach
    void setUp() {
        createReminderUseCase = new CreateReminderUseCase(
                reminderRepository,
                userRepository,
                notificationRepository,
                currentUserProvider,
                systemAuditLogService
        );
    }

    private UserEntity createTestUser(UUID id, String fullName) {
        return UserEntity.builder()
                .userId(id)
                .fullName(fullName)
                .build();
    }

    private CreateReminderRequest buildValidRequest() {
        CreateReminderRequest req = new CreateReminderRequest();
        req.setTitle("Follow up quotation");
        req.setRemindAt(OffsetDateTime.now().plusDays(3));
        req.setPriority("HIGH");
        req.setRelatedEntity("QUOTATION");
        req.setRelatedId(UUID.randomUUID());
        return req;
    }

    // ==========================================
    // NORMAL (N) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-CREATE-N01: Create reminder self-assigned (no notification sent)")
    void execute_selfAssignment_savesReminderAndNoNotification() {
        UUID creatorId = UUID.randomUUID();
        UserEntity creator = createTestUser(creatorId, "Creator User");
        when(currentUserProvider.resolve(null)).thenReturn(creator);

        CreateReminderRequest req = buildValidRequest();
        req.setAssignedUserId(null);

        ReminderEntity savedEntity = ReminderEntity.builder()
                .reminderId(UUID.randomUUID())
                .user(creator)
                .createdBy(creator)
                .title(req.getTitle())
                .remindAt(req.getRemindAt())
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(savedEntity);

        ReminderResponse response = createReminderUseCase.execute(req);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo(req.getTitle());
        verify(reminderRepository).save(any(ReminderEntity.class));
        verifyNoInteractions(notificationRepository);
        verify(systemAuditLogService).log(eq("REMINDER"), eq("REMINDER"), any(), eq("CREATED"), eq(creator), any(), any(), any());
    }

    @Test
    @DisplayName("UT-REM-CREATE-N02: Create reminder assigned to another user (notification sent)")
    void execute_assignToOther_savesReminderAndSendsNotification() {
        UUID creatorId = UUID.randomUUID();
        UserEntity creator = createTestUser(creatorId, "Creator User");
        UUID assigneeId = UUID.randomUUID();
        UserEntity assignee = createTestUser(assigneeId, "Assignee User");

        when(currentUserProvider.resolve(null)).thenReturn(creator);
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));

        CreateReminderRequest req = buildValidRequest();
        req.setAssignedUserId(assigneeId);

        ReminderEntity savedEntity = ReminderEntity.builder()
                .reminderId(UUID.randomUUID())
                .user(assignee)
                .createdBy(creator)
                .title(req.getTitle())
                .remindAt(req.getRemindAt())
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(savedEntity);

        ReminderResponse response = createReminderUseCase.execute(req);

        assertThat(response).isNotNull();
        verify(reminderRepository).save(any(ReminderEntity.class));
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(systemAuditLogService).log(eq("REMINDER"), eq("REMINDER"), any(), eq("CREATED"), eq(creator), any(), any(), any());
    }

    // ==========================================
    // ABNORMAL (A) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-CREATE-A01: Past due date triggers BusinessException with BAD_REQUEST")
    void execute_pastDueDate_throwsBusinessException() {
        CreateReminderRequest req = buildValidRequest();
        req.setRemindAt(OffsetDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> createReminderUseCase.execute(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DUE_DATE");

        verifyNoInteractions(reminderRepository, notificationRepository);
    }

    @Test
    @DisplayName("UT-REM-CREATE-A02: Non-existent assigned user triggers ResourceNotFoundException")
    void execute_nonExistentAssignee_throwsResourceNotFoundException() {
        UUID creatorId = UUID.randomUUID();
        UserEntity creator = createTestUser(creatorId, "Creator User");
        UUID assigneeId = UUID.randomUUID();

        when(currentUserProvider.resolve(null)).thenReturn(creator);
        when(userRepository.findById(assigneeId)).thenReturn(Optional.empty());

        CreateReminderRequest req = buildValidRequest();
        req.setAssignedUserId(assigneeId);

        assertThatThrownBy(() -> createReminderUseCase.execute(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining(assigneeId.toString());

        verifyNoInteractions(reminderRepository, notificationRepository);
    }

    // ==========================================
    // BOUNDARY (B) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-CREATE-B01: Due date exactly 5 seconds in future (near-now boundary) is accepted")
    void execute_remindAtExactlyBoundaryFuture_succeeds() {
        UUID creatorId = UUID.randomUUID();
        UserEntity creator = createTestUser(creatorId, "Creator User");
        when(currentUserProvider.resolve(null)).thenReturn(creator);

        CreateReminderRequest req = buildValidRequest();
        req.setRemindAt(OffsetDateTime.now().plusSeconds(5));

        ReminderEntity savedEntity = ReminderEntity.builder()
                .reminderId(UUID.randomUUID())
                .user(creator)
                .createdBy(creator)
                .title(req.getTitle())
                .remindAt(req.getRemindAt())
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(savedEntity);

        ReminderResponse response = createReminderUseCase.execute(req);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("UT-REM-CREATE-B02: Invalid priority fallback to MEDIUM")
    void execute_invalidPriorityString_fallsBackToMedium() {
        UUID creatorId = UUID.randomUUID();
        UserEntity creator = createTestUser(creatorId, "Creator");
        when(currentUserProvider.resolve(null)).thenReturn(creator);

        CreateReminderRequest req = buildValidRequest();
        req.setPriority("SUPER_HIGH");

        ReminderEntity savedEntity = ReminderEntity.builder()
                .reminderId(UUID.randomUUID())
                .user(creator)
                .createdBy(creator)
                .title(req.getTitle())
                .remindAt(req.getRemindAt())
                .priority(ReminderPriority.MEDIUM)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.save(any(ReminderEntity.class))).thenReturn(savedEntity);

        ReminderResponse response = createReminderUseCase.execute(req);

        assertThat(response.getPriority()).isEqualTo("MEDIUM");
        verify(reminderRepository).save(argThat(reminder -> reminder.getPriority() == ReminderPriority.MEDIUM));
    }
}
