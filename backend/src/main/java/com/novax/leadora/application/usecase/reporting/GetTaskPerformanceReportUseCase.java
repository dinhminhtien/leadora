package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse.StaffRow;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-23.2 — View Follow-up Task Performance Report.
 *
 * <p>Scope follows the caller: Sales Manager / Admin see the whole team, everyone else sees only
 * the tasks assigned to them. The scope is applied in SQL, so it cannot be bypassed by a caller
 * that forgets to filter afterwards.
 */
@Service
@RequiredArgsConstructor
public class GetTaskPerformanceReportUseCase {

    private static final int MAX_STAFF = 50;
    /** Roles that may see team-wide task performance; everyone else is scoped to their own tasks. */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");
    /** BR-17: a task in one of these is finished, so it can never be overdue. */
    private static final Set<TaskStatus> FINISHED_STATUSES =
            EnumSet.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
    private static final String UNASSIGNED_LABEL = "(Unassigned)";

    private final TaskRepository taskRepository;
    private final ReportRangeFactory reportRangeFactory;

    /**
     * The cache key is derived from {@link #scopeKey(UserEntity)} rather than repeating the role
     * test as a SpEL string: two copies of the same rule drift apart, and the copy that drifts here
     * would serve one user's task counts to another.
     *
     * @param actor the authenticated caller — determines whether the report is team-wide or own-only
     */
    @Cacheable(value = "task-performance-report",
            key = "T(com.novax.leadora.application.usecase.reporting.GetTaskPerformanceReportUseCase)"
                    + ".scopeKey(#actor) + '_' + #from + '_' + #to",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public TaskPerformanceReportResponse execute(UserEntity actor, LocalDate from, LocalDate to) {
        ReportRange range = reportRangeFactory.resolve(from, to);
        OffsetDateTime now = OffsetDateTime.now();

        boolean teamWide = canSeeAllTasks(actor);
        UUID assignedUserId = teamWide ? null : (actor == null ? null : actor.getUserId());

        Map<TaskStatus, Long> statusCounts = ReportingUtils.countByKey(
                taskRepository.aggregateByStatus(assignedUserId, range.start(), range.endExclusive()));
        Map<TaskPriority, Long> priorityCounts = ReportingUtils.countByKey(
                taskRepository.aggregateByPriority(assignedUserId, range.start(), range.endExclusive()));
        long overdue = taskRepository.countOverdue(
                assignedUserId, range.start(), range.endExclusive(), now, FINISHED_STATUSES);

        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long completed = ReportingUtils.countOf(statusCounts, TaskStatus.COMPLETED);

        return TaskPerformanceReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalTasks(total)
                .completed(completed)
                .open(ReportingUtils.countOf(statusCounts, TaskStatus.OPEN))
                .cancelled(ReportingUtils.countOf(statusCounts, TaskStatus.CANCELLED))
                .overdue(overdue)
                .completionRate(ReportingUtils.calculateRate(completed, total))
                .overdueRate(ReportingUtils.calculateRate(overdue, total))
                .priorityLow(ReportingUtils.countOf(priorityCounts, TaskPriority.LOW))
                .priorityMedium(ReportingUtils.countOf(priorityCounts, TaskPriority.MEDIUM))
                .priorityHigh(ReportingUtils.countOf(priorityCounts, TaskPriority.HIGH))
                .ownScope(!teamWide)
                .staff(buildStaff(assignedUserId, range, now))
                .build();
    }

    private List<StaffRow> buildStaff(UUID assignedUserId, ReportRange range, OffsetDateTime now) {
        List<StaffRow> named = new ArrayList<>();
        StaffRow unassigned = null;

        for (Object[] row : taskRepository.aggregateByOwner(
                assignedUserId, range.start(), range.endExclusive(), now,
                TaskStatus.COMPLETED, FINISHED_STATUSES)) {
            boolean isUnassigned = !(row[0] instanceof UUID);
            long total = ReportingUtils.toLong(row[2]);
            long completed = ReportingUtils.toLong(row[3]);
            if (isUnassigned && total == 0) {
                continue;
            }
            StaffRow staffRow = StaffRow.builder()
                    .name(isUnassigned
                            ? UNASSIGNED_LABEL
                            : (row[1] instanceof String name && !name.isBlank() ? name : row[0].toString()))
                    .total(total)
                    .completed(completed)
                    .overdue(ReportingUtils.toLong(row[4]))
                    .completionRate(ReportingUtils.calculateRate(completed, total))
                    .unassigned(isUnassigned)
                    .build();
            if (isUnassigned) {
                unassigned = staffRow;
            } else {
                named.add(staffRow);
            }
        }

        named.sort(Comparator.comparingLong(StaffRow::getTotal).reversed());
        List<StaffRow> rows = new ArrayList<>(
                named.size() > MAX_STAFF ? named.subList(0, MAX_STAFF) : named);
        // Kept out of the cap so the breakdown always reconciles with the headline total.
        if (unassigned != null) {
            rows.add(unassigned);
        }
        return rows;
    }

    /**
     * The cache partition for a caller: one shared entry for every team-wide viewer, one private
     * entry per scoped user. Public and static because the {@code @Cacheable} SpEL expression calls
     * it — that is what keeps the key and the query filter derived from the same rule.
     */
    public static String scopeKey(UserEntity user) {
        return canSeeAllTasks(user) ? "all" : String.valueOf(user == null ? "anonymous" : user.getUserId());
    }

    private static boolean canSeeAllTasks(UserEntity user) {
        String role = (user != null && user.getRole() != null && user.getRole().getRoleName() != null)
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
        return FULL_SCOPE_ROLES.contains(role);
    }
}
