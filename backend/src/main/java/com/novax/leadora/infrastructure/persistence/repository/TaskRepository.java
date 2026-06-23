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

        // ── Lightweight association lookups (no eager load required) ──────────

        List<TaskEntity> findByAssignedUser_UserId(UUID assignedUserId);

        List<TaskEntity> findByStatus(TaskStatus status);

        List<TaskEntity> findByDeal_DealId(UUID dealId);

        List<TaskEntity> findByCustomer_CustomerId(UUID customerId);

        List<TaskEntity> findByLead_LeadId(UUID leadId);
}
