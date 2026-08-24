package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskStatsResponse;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * UC-10.2 / UC-10.5 — Aggregate count stats across the entire filtered task dataset.
 *
 * <p>Counted in the database over the whole set matching the current scope, search,
 * and priority filters, rather than derived in the browser from the single page currently displayed.
 */
@Service
@RequiredArgsConstructor
public class GetTaskStatsUseCase {

    private final TaskRepository taskRepository;
    private final TaskAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public TaskStatsResponse execute(
            String search,
            String priority,
            String assignedUserId,
            String customerId
    ) {
        UserEntity currentUser = accessPolicy.currentUser();
        UUID scopedOwnerId = accessPolicy.listScopeOwnerId(currentUser);
        UUID effectiveAssignee = scopedOwnerId != null ? scopedOwnerId : parseUuid(assignedUserId);

        Specification<TaskEntity> base = Specification.allOf(
                TaskSpecification.search(StringUtils.hasText(search) ? search.trim() : null),
                TaskSpecification.hasPriority(parseEnum(TaskPriority.class, priority)),
                TaskSpecification.assignedTo(effectiveAssignee),
                TaskSpecification.forCustomer(parseUuid(customerId))
        );

        long total = taskRepository.count(base);
        long open = taskRepository.count(Specification.allOf(base, TaskSpecification.hasStatus(TaskStatus.OPEN)));
        long completed = taskRepository.count(Specification.allOf(base, TaskSpecification.hasStatus(TaskStatus.COMPLETED)));
        long overdue = taskRepository.count(Specification.allOf(base, TaskSpecification.isOverdue()));

        return TaskStatsResponse.builder()
                .total(total)
                .open(open)
                .completed(completed)
                .overdue(overdue)
                .build();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
