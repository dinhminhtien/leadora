package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.TaskStatusCount;
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

        // ── Performance report query (eliminates N+1 and filters at DB level) ──
        @EntityGraph(attributePaths = { "assignedUser" })
        @Query("""
                        SELECT t FROM TaskEntity t
                        WHERE (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
                          AND t.createdAt >= :startDate
                          AND t.createdAt <= :endDate
                        """)
        List<TaskEntity> findForPerformanceReport(
                        @Param("assignedUserId") UUID assignedUserId,
                        @Param("startDate") OffsetDateTime startDate,
                        @Param("endDate") OffsetDateTime endDate);

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

        @Query("""
                        SELECT new com.novax.leadora.application.usecase.chat.dto.TaskStatusCount(t.status, COUNT(t))
                        FROM TaskEntity t
                        WHERE (:userId IS NULL OR t.assignedUser.userId = :userId)
                        GROUP BY t.status
                        """)
        List<TaskStatusCount> countByStatusForChat(@Param("userId") UUID userId);

        @Query("""
                        SELECT COUNT(t) FROM TaskEntity t
                        WHERE (:userId IS NULL OR t.assignedUser.userId = :userId)
                          AND t.status NOT IN :closedStatuses
                          AND t.endAt IS NOT NULL
                          AND t.endAt < :now
                        """)
        long countOverdueForChat(@Param("userId") UUID userId,
                        @Param("closedStatuses") List<TaskStatus> closedStatuses,
                        @Param("now") OffsetDateTime now);

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
