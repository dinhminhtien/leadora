package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.request.CreateTaskRequest;
import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTaskUseCase {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;

    @Transactional
    public TaskResponse execute(CreateTaskRequest request) {
        UserEntity assignedUser = userRepository.findById(request.getAssignedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()));

        TaskPriority priority = TaskPriority.MEDIUM;
        if (StringUtils.hasText(request.getPriority())) {
            try {
                priority = TaskPriority.valueOf(request.getPriority().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        LeadEntity lead = null;
        if (request.getLeadId() != null) {
            lead = leadRepository.findById(request.getLeadId()).orElse(null);
        }

        CustomerEntity customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId()).orElse(null);
        }

        DealEntity deal = null;
        if (request.getDealId() != null) {
            deal = dealRepository.findById(request.getDealId()).orElse(null);
        }

        if (request.getStartAt() != null && request.getEndAt() != null
                && !request.getStartAt().isBefore(request.getEndAt())) {
            throw new IllegalArgumentException("start_at must be before end_at");
        }

        // Auto-derive due_date from start_at when not explicitly provided
        java.time.LocalDate dueDate = request.getDueDate();
        if (dueDate == null && request.getStartAt() != null) {
            dueDate = request.getStartAt().toLocalDate();
        }

        TaskEntity task = TaskEntity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(priority)
                .status(TaskStatus.OPEN)
                .dueDate(dueDate)
                .resultNote(request.getResultNote())
                .assignedUser(assignedUser)
                .lead(lead)
                .customer(customer)
                .deal(deal)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .primaryContactName(request.getPrimaryContactName())
                .primaryContactPhone(request.getPrimaryContactPhone())
                .build();

        TaskEntity saved = taskRepository.save(task);

        // UC-17.2: start SLA tracking — non-fatal if no rule configured
        try {
            startSlaTrackingUseCase.execute("FOLLOW_UP_TASK", "TASK", saved.getTaskId());
        } catch (Exception e) {
            log.warn("SLA tracking failed for task {}: {}", saved.getTaskId(), e.getMessage());
        }

        return TaskResponse.from(saved);
    }
}
