package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse.StaffRow;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UC-23.2 — View Follow-up Task Performance Report. */
@Service
@RequiredArgsConstructor
public class GetTaskPerformanceReportUseCase {

    private static final int MAX_STAFF = 50;
    /** Roles that may see team-wide task performance; everyone else is scoped to their own tasks. */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");

    private final TaskRepository taskRepository;

    /**
     * @param actor the authenticated user — UC-23.2 access scope: Sales Manager/Admin see team-wide
     *              performance, Sales Staff see only their own assigned tasks.
     */
    @Transactional(readOnly = true)
    public TaskPerformanceReportResponse execute(UserEntity actor, LocalDate from, LocalDate to) {
        OffsetDateTime now = OffsetDateTime.now();

        // Scope by role at the query level (Sales Staff → own tasks only) so the report never
        // exposes other people's follow-up data and stays cheap for individual contributors.
        List<TaskEntity> source = canSeeAllTasks(actor)
                ? taskRepository.findAll()
                : taskRepository.findByAssignedUser_UserId(actor.getUserId());
        List<TaskEntity> tasks = source.stream()
                .filter(t -> inRange(t.getCreatedAt(), from, to))
                .toList();

        long total = tasks.size();
        long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long open = tasks.stream().filter(t -> t.getStatus() == TaskStatus.OPEN).count();
        long cancelled = tasks.stream().filter(t -> t.getStatus() == TaskStatus.CANCELLED).count();
        long overdue = tasks.stream().filter(t -> isOverdue(t, now)).count();

        long low = tasks.stream().filter(t -> t.getPriority() == TaskPriority.LOW).count();
        long medium = tasks.stream().filter(t -> t.getPriority() == TaskPriority.MEDIUM).count();
        long high = tasks.stream().filter(t -> t.getPriority() == TaskPriority.HIGH).count();

        return TaskPerformanceReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalTasks(total)
                .completed(completed)
                .open(open)
                .cancelled(cancelled)
                .overdue(overdue)
                .completionRate(rate(completed, total))
                .overdueRate(rate(overdue, total))
                .priorityLow(low)
                .priorityMedium(medium)
                .priorityHigh(high)
                .staff(buildStaff(tasks, now))
                .build();
    }

    private List<StaffRow> buildStaff(List<TaskEntity> tasks, OffsetDateTime now) {
        Map<UUID, StaffAgg> byUser = new LinkedHashMap<>();
        for (TaskEntity t : tasks) {
            UserEntity u = t.getAssignedUser();
            if (u == null || u.getUserId() == null) {
                continue;
            }
            StaffAgg a = byUser.computeIfAbsent(u.getUserId(), id -> {
                StaffAgg s = new StaffAgg();
                s.name = u.getFullName() != null ? u.getFullName() : id.toString();
                return s;
            });
            a.total++;
            if (t.getStatus() == TaskStatus.COMPLETED) a.completed++;
            if (isOverdue(t, now)) a.overdue++;
        }

        List<StaffRow> rows = new ArrayList<>();
        for (StaffAgg a : byUser.values()) {
            rows.add(StaffRow.builder()
                    .name(a.name)
                    .total(a.total)
                    .completed(a.completed)
                    .overdue(a.overdue)
                    .completionRate(rate(a.completed, a.total))
                    .build());
        }
        rows.sort(Comparator.comparingLong((StaffRow r) -> r.getTotal()).reversed());
        return rows.size() > MAX_STAFF ? rows.subList(0, MAX_STAFF) : rows;
    }

    /** Overdue = not finished/cancelled and past its end_at (BR-17 derived flag). */
    private boolean isOverdue(TaskEntity t, OffsetDateTime now) {
        if (t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED) {
            return false;
        }
        return t.getEndAt() != null && t.getEndAt().isBefore(now);
    }

    private boolean canSeeAllTasks(UserEntity user) {
        String role = (user != null && user.getRole() != null && user.getRole().getRoleName() != null)
                ? user.getRole().getRoleName().trim().toUpperCase() : "";
        return FULL_SCOPE_ROLES.contains(role);
    }

    private boolean inRange(OffsetDateTime at, LocalDate from, LocalDate to) {
        if (at == null) {
            return from == null && to == null;
        }
        LocalDate d = at.toLocalDate();
        if (from != null && d.isBefore(from)) return false;
        return to == null || !d.isAfter(to);
    }

    private double rate(long part, long whole) {
        if (whole <= 0) return 0;
        return Math.round((part * 10000.0 / whole)) / 100.0;
    }

    private static class StaffAgg {
        String name;
        long total;
        long completed;
        long overdue;
    }
}
