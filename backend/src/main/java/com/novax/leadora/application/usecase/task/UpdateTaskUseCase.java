package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.request.UpdateTaskRequest;
import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateTaskUseCase {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;

    @Transactional
    public TaskResponse execute(UUID taskId, UpdateTaskRequest request) {
        TaskEntity task = taskRepository.findWithRelationsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (StringUtils.hasText(request.getTitle())) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getResultNote() != null) {
            task.setResultNote(request.getResultNote());
        }

        if (StringUtils.hasText(request.getPriority())) {
            try {
                task.setPriority(TaskPriority.valueOf(request.getPriority().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        if (StringUtils.hasText(request.getStatus())) {
            try {
                TaskStatus newStatus = TaskStatus.valueOf(request.getStatus().toUpperCase());
                if (task.getStatus() == TaskStatus.COMPLETED && newStatus == TaskStatus.CANCELLED) {
                    throw new BusinessException(
                            "TASK_INVALID_STATUS_TRANSITION",
                            "Completed tasks cannot be cancelled directly. Please reopen the task first.",
                            HttpStatus.CONFLICT);
                }
                task.setStatus(newStatus);
            } catch (IllegalArgumentException ignored) {}
        }

        if (request.getAssignedUserId() != null) {
            UserEntity assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()));
            task.setAssignedUser(assignedUser);
        }

        if (request.getLeadId() != null) {
            task.setLead(leadRepository.findById(request.getLeadId()).orElse(null));
        }
        if (request.getCustomerId() != null) {
            task.setCustomer(customerRepository.findById(request.getCustomerId()).orElse(null));
        }
        if (request.getDealId() != null) {
            task.setDeal(dealRepository.findById(request.getDealId()).orElse(null));
        }

        if (request.getStartAt() != null && request.getEndAt() != null
                && !request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException(
                    "INVALID_SCHEDULE",
                    "End time must be later than start time.",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.getStartAt() != null) {
            task.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            task.setEndAt(request.getEndAt());
        }
        if (request.getPrimaryContactName() != null) {
            task.setPrimaryContactName(request.getPrimaryContactName());
        }
        if (request.getPrimaryContactPhone() != null) {
            task.setPrimaryContactPhone(request.getPrimaryContactPhone());
        }
        return TaskResponse.from(taskRepository.save(task));
    }
}
