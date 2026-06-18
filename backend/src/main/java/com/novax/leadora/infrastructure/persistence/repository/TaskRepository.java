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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "lead", "customer", "deal"})
    @Query("SELECT t FROM TaskEntity t WHERE t.taskId = :taskId")
    Optional<TaskEntity> findWithRelationsById(@Param("taskId") UUID taskId);

    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "lead", "customer", "deal"})
    @Query(value = """
            SELECT t FROM TaskEntity t
            WHERE (:search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
              AND (:overdue = false OR (t.dueDate < CURRENT_DATE
                   AND t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.COMPLETED
                   AND t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.CANCELLED))
            """,
            countQuery = """
            SELECT COUNT(t) FROM TaskEntity t
            WHERE (:search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:assignedUserId IS NULL OR t.assignedUser.userId = :assignedUserId)
              AND (:overdue = false OR (t.dueDate < CURRENT_DATE
                   AND t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.COMPLETED
                   AND t.status <> com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus.CANCELLED))
            """)
    Page<TaskEntity> searchTasks(
            @Param("search") String search,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("assignedUserId") UUID assignedUserId,
            @Param("overdue") boolean overdue,
            Pageable pageable
    );

    List<TaskEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<TaskEntity> findByStatus(TaskStatus status);
    List<TaskEntity> findByDeal_DealId(UUID dealId);
    List<TaskEntity> findByCustomer_CustomerId(UUID customerId);
    List<TaskEntity> findByLead_LeadId(UUID leadId);
    List<TaskEntity> findByAssignedUser_UserIdAndStatusAndDueDateBefore(UUID assignedUserId, TaskStatus status, LocalDate date);
}
