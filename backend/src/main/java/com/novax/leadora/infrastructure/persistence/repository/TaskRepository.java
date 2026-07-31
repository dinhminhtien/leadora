package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID>, JpaSpecificationExecutor<TaskEntity> {

        // ── Single entity ──────────────────────────────────────────────────────

        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "lead", "customer", "deal" })
        @Query("SELECT t FROM TaskEntity t WHERE t.taskId = :taskId")
        Optional<TaskEntity> findWithRelationsById(@Param("taskId") UUID taskId);

        // ── Paginated list (dynamic filtering via Specification) ───────────────
        @Override
        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "lead", "customer", "deal" })
        Page<TaskEntity> findAll(Specification<TaskEntity> spec, Pageable pageable);

        // ── Calendar range query ───────────────────────────────────────────────
        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "lead", "customer", "deal" })
        @Query("""
                        SELECT t FROM TaskEntity t
                        WHERE (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
                          AND t.startAt IS NOT NULL
                          AND t.startAt <= :rangeEnd
                          AND (t.endAt IS NULL OR t.endAt >= :rangeStart)
                        ORDER BY t.startAt ASC
                        """)
        List<TaskEntity> findByDateRange(
                        @Param("assignedUserId") UUID assignedUserId,
                        @Param("rangeStart") OffsetDateTime rangeStart,
                        @Param("rangeEnd") OffsetDateTime rangeEnd);

        // ── UC-23.2 report aggregates ──────────────────────────────────────────
        // A null :assignedUserId means team-wide (Manager/Admin); a value scopes to that user's
        // own tasks. The join is LEFT so the team-wide scope still sees unassigned tasks — the
        // previous `t.assignedUser.userId = :id` form created an implicit INNER join that dropped
        // them from the manager's totals even when the parameter was null.
        // Ranges are half-open: [start, end) — see ReportRange.

        /** {@code [status, count]} rows. */
        @Query("""
                        SELECT t.status, count(t)
                        FROM TaskEntity t LEFT JOIN t.assignedUser u
                        WHERE (:assignedUserId IS NULL OR u.userId = :assignedUserId)
                          AND t.createdAt >= :start
                          AND t.createdAt < :end
                        GROUP BY t.status
                        """)
        List<Object[]> aggregateByStatus(
                        @Param("assignedUserId") UUID assignedUserId,
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end);

        /** {@code [priority, count]} rows. */
        @Query("""
                        SELECT t.priority, count(t)
                        FROM TaskEntity t LEFT JOIN t.assignedUser u
                        WHERE (:assignedUserId IS NULL OR u.userId = :assignedUserId)
                          AND t.createdAt >= :start
                          AND t.createdAt < :end
                        GROUP BY t.priority
                        """)
        List<Object[]> aggregateByPriority(
                        @Param("assignedUserId") UUID assignedUserId,
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end);

        /**
         * Overdue count (BR-17): past {@code end_at} and not finished. "Overdue" is a derived flag,
         * never a stored status, so the cut-off instant is passed in rather than read from a column.
         */
        @Query("""
                        SELECT count(t)
                        FROM TaskEntity t LEFT JOIN t.assignedUser u
                        WHERE (:assignedUserId IS NULL OR u.userId = :assignedUserId)
                          AND t.createdAt >= :start
                          AND t.createdAt < :end
                          AND t.endAt IS NOT NULL
                          AND t.endAt < :now
                          AND t.status NOT IN :finishedStatuses
                        """)
        long countOverdue(
                        @Param("assignedUserId") UUID assignedUserId,
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end,
                        @Param("now") OffsetDateTime now,
                        @Param("finishedStatuses") Collection<TaskStatus> finishedStatuses);

        /** {@code [ownerId, ownerName, total, completed, overdue]} rows. */
        @Query("""
                        SELECT u.userId, u.fullName, count(t),
                               sum(CASE WHEN t.status = :completedStatus THEN 1 ELSE 0 END),
                               sum(CASE WHEN t.endAt IS NOT NULL AND t.endAt < :now
                                         AND t.status NOT IN :finishedStatuses THEN 1 ELSE 0 END)
                        FROM TaskEntity t LEFT JOIN t.assignedUser u
                        WHERE (:assignedUserId IS NULL OR u.userId = :assignedUserId)
                          AND t.createdAt >= :start
                          AND t.createdAt < :end
                        GROUP BY u.userId, u.fullName
                        """)
        List<Object[]> aggregateByOwner(
                        @Param("assignedUserId") UUID assignedUserId,
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end,
                        @Param("now") OffsetDateTime now,
                        @Param("completedStatus") TaskStatus completedStatus,
                        @Param("finishedStatuses") Collection<TaskStatus> finishedStatuses);

        // ── Lightweight association lookups (no eager load required) ──────────

        List<TaskEntity> findByAssignedUser_UserId(UUID assignedUserId);

        List<TaskEntity> findByStatus(TaskStatus status);

        List<TaskEntity> findByDeal_DealId(UUID dealId);

        List<TaskEntity> findByCustomer_CustomerId(UUID customerId);

        List<TaskEntity> findByLead_LeadId(UUID leadId);

        long countByStatusNotIn(List<TaskStatus> statuses);

        long countByStatusNotInAndEndAtBefore(List<TaskStatus> statuses, OffsetDateTime endAt);

        List<TaskEntity> findByStatusAndEndAtBeforeAndOverdueNotifiedFalse(TaskStatus status, OffsetDateTime endAt);

        // ── Chat-assistant snapshot ────────────────────────────────────────────
        // A null :userId means "every task" (Manager/Admin scope); a non-null value restricts to
        // that user's records. "Overdue" is derived (BR-17), not stored: not closed, and past
        // end_at — there is no due_date column and no OVERDUE status.

        /**
         * Tasks still to be done, earliest deadline first — overdue ones therefore come first.
         *
         * <p>Listing only the overdue ones (as this did originally) leaves the assistant unable to
         * name a single task whenever nothing has slipped, which is the normal case: the assistant
         * could report "3 open tasks" but not what they were.
         */
        @EntityGraph(attributePaths = { "assignedUser" })
        @Query("""
                        SELECT t FROM TaskEntity t
                        WHERE (:userId IS NULL OR t.assignedUser.userId = :userId)
                          AND t.status NOT IN :closedStatuses
                        ORDER BY t.endAt ASC NULLS LAST
                        """)
        List<TaskEntity> findOpenForChat(@Param("userId") UUID userId,
                        @Param("closedStatuses") List<TaskStatus> closedStatuses,
                        Pageable pageable);
}
