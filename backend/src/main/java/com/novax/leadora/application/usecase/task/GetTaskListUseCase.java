package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTaskListUseCase {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public Page<TaskResponse> execute(
            String search,
            String status,
            String priority,
            String assignedUserId,
            String customerId,
            boolean overdue,
            int page,
            int size
    ) {
        // Sort is defined by TaskSpecification.defaultSort() — pass unsorted Pageable.
        Specification<TaskEntity> spec = Specification.allOf(
                TaskSpecification.search(StringUtils.hasText(search) ? search.trim() : null),
                TaskSpecification.hasStatus(parseEnum(TaskStatus.class, status)),
                TaskSpecification.hasPriority(parseEnum(TaskPriority.class, priority)),
                TaskSpecification.assignedTo(parseUuid(assignedUserId)),
                TaskSpecification.forCustomer(parseUuid(customerId)),
                overdue ? TaskSpecification.isOverdue() : null,
                TaskSpecification.defaultSort()
        );

        return taskRepository.findAll(spec, PageRequest.of(page, size)).map(TaskResponse::from);
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
