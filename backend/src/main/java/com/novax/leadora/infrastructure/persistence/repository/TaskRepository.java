package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<TaskEntity> findByStatus(TaskStatus status);
    List<TaskEntity> findByDeal_DealId(UUID dealId);
    List<TaskEntity> findByCustomer_CustomerId(UUID customerId);
    List<TaskEntity> findByLead_LeadId(UUID leadId);
    List<TaskEntity> findByAssignedUser_UserIdAndStatusAndDueDateBefore(UUID assignedUserId, TaskStatus status, LocalDate date);
}
