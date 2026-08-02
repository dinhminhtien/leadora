package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-23.2 — role scoping, the derived overdue flag (BR-17), and per-staff reconciliation. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetTaskPerformanceReportUseCaseTest {

    @Mock
    private TaskRepository taskRepository;

    private GetTaskPerformanceReportUseCase useCase;
    private final List<Object[]> statusRows = new ArrayList<>();
    private final List<Object[]> priorityRows = new ArrayList<>();
    private final List<Object[]> ownerRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new GetTaskPerformanceReportUseCase(
                taskRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(taskRepository.aggregateByStatus(any(), any(), any())).thenReturn(statusRows);
        when(taskRepository.aggregateByPriority(any(), any(), any())).thenReturn(priorityRows);
        when(taskRepository.aggregateByOwner(any(), any(), any(), any(), any(), any()))
                .thenReturn(ownerRows);
        when(taskRepository.countOverdue(any(), any(), any(), any(), any())).thenReturn(0L);
    }

    private UserEntity user(String roleName) {
        RoleEntity role = new RoleEntity();
        role.setRoleName(roleName);
        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private TaskPerformanceReportResponse run(UserEntity actor) {
        return useCase.execute(actor, LocalDate.now().minusDays(30), LocalDate.now());
    }

    @Test
    @DisplayName("a Manager sees the whole team — the query is not filtered by owner")
    void managerScopeIsTeamWide() {
        TaskPerformanceReportResponse report = run(user("MANAGER"));

        verify(taskRepository).aggregateByStatus(isNull(), any(), any());
        assertThat(report.isOwnScope()).isFalse();
    }

    @Test
    @DisplayName("a Sales Staff sees only their own tasks — the filter is applied in SQL")
    void salesScopeIsOwnTasksOnly() {
        UserEntity sales = user("SALES");

        TaskPerformanceReportResponse report = run(sales);

        ArgumentCaptor<UUID> ownerId = ArgumentCaptor.forClass(UUID.class);
        verify(taskRepository).aggregateByStatus(ownerId.capture(), any(), any());
        assertThat(ownerId.getValue()).isEqualTo(sales.getUserId());
        assertThat(report.isOwnScope()).isTrue();
    }

    @Test
    @DisplayName("the cache key separates each scoped user but shares one entry per team-wide role")
    void cacheKeyMatchesTheQueryScope() {
        UserEntity sales = user("SALES");
        UserEntity otherSales = user("SALES");

        assertThat(GetTaskPerformanceReportUseCase.scopeKey(user("MANAGER"))).isEqualTo("all");
        assertThat(GetTaskPerformanceReportUseCase.scopeKey(user("ADMIN"))).isEqualTo("all");
        assertThat(GetTaskPerformanceReportUseCase.scopeKey(sales))
                .isNotEqualTo(GetTaskPerformanceReportUseCase.scopeKey(otherSales));
    }

    @Test
    @DisplayName("role matching tolerates padding and case")
    void roleMatchingIsForgiving() {
        assertThat(GetTaskPerformanceReportUseCase.scopeKey(user("  manager  "))).isEqualTo("all");
    }

    @Test
    @DisplayName("overdue excludes finished tasks (BR-17)")
    @SuppressWarnings("unchecked")
    void overdueNeverIncludesFinishedTasks() {
        run(user("MANAGER"));

        ArgumentCaptor<Collection<TaskStatus>> finished = ArgumentCaptor.forClass(Collection.class);
        verify(taskRepository).countOverdue(any(), any(), any(), any(), finished.capture());
        assertThat(finished.getValue())
                .containsExactlyInAnyOrder(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
    }

    @Test
    @DisplayName("completion and overdue rates are computed against the same total")
    void ratesShareOneDenominator() {
        statusRows.add(new Object[] { TaskStatus.COMPLETED, 6L });
        statusRows.add(new Object[] { TaskStatus.OPEN, 3L });
        statusRows.add(new Object[] { TaskStatus.CANCELLED, 1L });
        when(taskRepository.countOverdue(any(), any(), any(), any(), any())).thenReturn(2L);

        TaskPerformanceReportResponse report = run(user("MANAGER"));

        assertThat(report.getTotalTasks()).isEqualTo(10);
        assertThat(report.getCompletionRate()).isEqualTo(60.0);
        assertThat(report.getOverdueRate()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("priority distribution is read straight from the grouped query")
    void priorityDistribution() {
        priorityRows.add(new Object[] { TaskPriority.HIGH, 4L });
        priorityRows.add(new Object[] { TaskPriority.LOW, 1L });

        TaskPerformanceReportResponse report = run(user("MANAGER"));

        assertThat(report.getPriorityHigh()).isEqualTo(4);
        assertThat(report.getPriorityLow()).isEqualTo(1);
        assertThat(report.getPriorityMedium()).isZero();
    }

    @Test
    @DisplayName("unassigned tasks get their own row so the breakdown reconciles")
    @SuppressWarnings("null")
    void unassignedTasksAreKept() {
        statusRows.add(new Object[] { TaskStatus.OPEN, 10L });
        ownerRows.add(new Object[] { UUID.randomUUID(), "Mai Anh", 7L, 3L, 1L });
        ownerRows.add(new Object[] { null, null, 3L, 0L, 2L });

        TaskPerformanceReportResponse report = run(user("MANAGER"));

        assertThat(report.getStaff()).hasSize(2);
        assertThat(report.getStaff().stream().mapToLong(row -> row.getTotal()).sum())
                .isEqualTo(report.getTotalTasks());
        assertThat(report.getStaff())
                .filteredOn(TaskPerformanceReportResponse.StaffRow::isUnassigned)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getName()).isEqualTo("(Unassigned)");
                    assertThat(row.getOverdue()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        TaskPerformanceReportResponse report = run(user("MANAGER"));

        assertThat(report.getTotalTasks()).isZero();
        assertThat(report.getCompletionRate()).isZero();
        assertThat(report.getOverdueRate()).isZero();
        assertThat(report.getStaff()).isEmpty();
    }
}
