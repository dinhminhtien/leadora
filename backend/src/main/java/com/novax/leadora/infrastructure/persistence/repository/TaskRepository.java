package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

  @EntityGraph(attributePaths = { "assignedUser", "createdBy", "lead", "customer", "deal" })
  @Query("SELECT t FROM TaskEntity t WHERE t.taskId = :taskId")
  Optional<TaskEntity> findWithRelationsById(@Param("taskId") UUID taskId);

  /**
   * Main task list / search query.
   * Sort: scheduled tasks first (startAt ASC NULLS LAST), then by createdAt DESC.
   */
  @EntityGraph(attributePaths = { "assignedUser", "createdBy", "lead", "customer", "deal" })
  @Query(value = """
      SELECT t FROM TaskEntity t
      WHERE (:search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:status IS NULL OR t.status = :status)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
        AND (:customerId IS NULL OR t.customer.customerId = :customerId)
        AND (:overdue = false OR (
              t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.COMPLETED
          AND t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.CANCELLED
          AND t.endAt IS NOT NULL AND t.endAt < CURRENT_TIMESTAMP
        ))
      ORDER BY
        CASE WHEN t.status = com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.OPEN THEN 0 ELSE 1 END ASC,
        CASE WHEN t.startAt IS NULL THEN 1 ELSE 0 END ASC,
        t.startAt ASC,
        t.updatedAt DESC
      """, countQuery = """
      SELECT COUNT(t) FROM TaskEntity t
      WHERE (:search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:status IS NULL OR t.status = :status)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
        AND (:customerId IS NULL OR t.customer.customerId = :customerId)
        AND (:overdue = false OR (
              t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.COMPLETED
          AND t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.CANCELLED
          AND t.endAt IS NOT NULL AND t.endAt < CURRENT_TIMESTAMP
        ))
      """)
  Page<TaskEntity> searchTasks(
      @Param("search") String search,
      @Param("status") TaskStatus status,
      @Param("priority") TaskPriority priority,
      @Param("assignedUserId") UUID assignedUserId,
      @Param("customerId") UUID customerId,
      @Param("overdue") boolean overdue,
      Pageable pageable);

  /**
   * Calendar / agenda range query — fetches tasks that overlap [rangeStart,
   * rangeEnd].
   */
  @EntityGraph(attributePaths = { "assignedUser", "createdBy", "lead", "customer", "deal" })
  @Query("""
      SELECT t FROM TaskEntity t
      WHERE (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
        AND t.startAt IS NOT NULL
        AND t.startAt <= :rangeEnd
        AND (t.endAt IS NULL OR t.endAt >= :rangeStart)
      ORDER BY
        t.startAt ASC
      """)
  List<TaskEntity> findByDateRange(
      @Param("assignedUserId") UUID assignedUserId,
      @Param("rangeStart") OffsetDateTime rangeStart,
      @Param("rangeEnd") OffsetDateTime rangeEnd);

  List<TaskEntity> findByAssignedUser_UserId(UUID assignedUserId);

  List<TaskEntity> findByStatus(TaskStatus status);

  List<TaskEntity> findByDeal_DealId(UUID dealId);

  List<TaskEntity> findByCustomer_CustomerId(UUID customerId);

  List<TaskEntity> findByLead_LeadId(UUID leadId);
}
