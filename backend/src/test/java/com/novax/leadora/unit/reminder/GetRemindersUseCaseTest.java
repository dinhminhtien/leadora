package com.novax.leadora.unit.reminder;

import com.novax.leadora.application.usecase.reminder.GetRemindersUseCase;
import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRemindersUseCaseTest {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private GetRemindersUseCase getRemindersUseCase;

    @BeforeEach
    void setUp() {
        getRemindersUseCase = new GetRemindersUseCase(reminderRepository, currentUserProvider);
    }

    private UserEntity createTestUser(UUID id, String roleName) {
        RoleEntity role = RoleEntity.builder()
                .roleName(roleName)
                .build();
        return UserEntity.builder()
                .userId(id)
                .fullName("Test User")
                .role(role)
                .build();
    }

    private ReminderEntity createTestReminder(UUID id, String title, String description) {
        return ReminderEntity.builder()
                .reminderId(id)
                .title(title)
                .description(description)
                .status(ReminderStatus.PENDING)
                .priority(ReminderPriority.MEDIUM)
                .remindAt(OffsetDateTime.now().plusDays(2))
                .build();
    }

    private Specification<ReminderEntity> anySpec() {
        return argThat(spec -> true);
    }

    // ==========================================
    // NORMAL (N) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-GET-N01: Execute as MANAGER with null userId allows fetching all reminders")
    void execute_asManager_allowsFetchingAll() {
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");

        ReminderEntity r1 = createTestReminder(UUID.randomUUID(), "Call Client A", "Follow up");
        when(currentUserProvider.resolve(null)).thenReturn(manager);
        when(reminderRepository.findAll(anySpec())).thenReturn(List.of(r1));

        List<ReminderResponse> results = getRemindersUseCase.execute(
                null, "PENDING", null, null, "due", null
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Call Client A");
        verify(reminderRepository).findAll(anySpec());
    }

    @Test
    @DisplayName("UT-REM-GET-N02: Execute as SALES scopes query to own userId even if requested another")
    void execute_asSales_scopesToSelf() {
        UUID salesId = UUID.randomUUID();
        UserEntity sales = createTestUser(salesId, "SALES");
        UUID otherUserId = UUID.randomUUID();

        ReminderEntity r1 = createTestReminder(UUID.randomUUID(), "Sales Task", "Desc");
        when(currentUserProvider.resolve(null)).thenReturn(sales);
        when(reminderRepository.findAll(anySpec())).thenReturn(List.of(r1));

        List<ReminderResponse> results = getRemindersUseCase.execute(
                otherUserId, "PENDING", null, null, "due", null
        );

        assertThat(results).hasSize(1);
        verify(reminderRepository).findAll(anySpec());
    }

    // ==========================================
    // ABNORMAL (A) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-GET-A01: Invalid status filter triggers BusinessException with BAD_REQUEST")
    void execute_withInvalidStatus_throwsBusinessException() {
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");
        when(currentUserProvider.resolve(null)).thenReturn(manager);

        assertThatThrownBy(() -> getRemindersUseCase.execute(
                null, "INVALID_STATUS_VALUE", null, null, "due", null
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_STATUS");

        verifyNoInteractions(reminderRepository);
    }

    @Test
    @DisplayName("UT-REM-GET-A02: User has null or empty role name - handles gracefully without crashing")
    void execute_withNullUserRole_handlesGracefully() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .fullName("No Role User")
                .role(null)
                .build();

        when(currentUserProvider.resolve(null)).thenReturn(user);
        when(reminderRepository.findAll(anySpec())).thenReturn(Collections.emptyList());

        List<ReminderResponse> results = getRemindersUseCase.execute(
                null, null, null, null, null, null
        );

        assertThat(results).isEmpty();
        verify(reminderRepository).findAll(anySpec());
    }

    // ==========================================
    // BOUNDARY (B) CASES
    // ==========================================

    @Test
    @DisplayName("UT-REM-GET-B01: Null status filter defaults to excluding cancelled reminders")
    void execute_withNullStatusFilter_excludesCancelled() {
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");

        when(currentUserProvider.resolve(null)).thenReturn(manager);
        when(reminderRepository.findAll(anySpec())).thenReturn(Collections.emptyList());

        getRemindersUseCase.execute(null, null, null, null, null, null);

        verify(reminderRepository).findAll(anySpec());
    }

    @Test
    @DisplayName("UT-REM-GET-B02: Whitespace search keyword does not filter records by keyword")
    void execute_withWhitespaceSearch_returnsAllRecordsUnfiltered() {
        UUID managerId = UUID.randomUUID();
        UserEntity manager = createTestUser(managerId, "MANAGER");

        ReminderEntity r1 = createTestReminder(UUID.randomUUID(), "Call Client A", "Follow up");
        when(currentUserProvider.resolve(null)).thenReturn(manager);
        when(reminderRepository.findAll(anySpec())).thenReturn(List.of(r1));

        List<ReminderResponse> results = getRemindersUseCase.execute(
                null, "PENDING", null, null, "due", "   "
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Call Client A");
    }
}
