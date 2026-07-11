package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
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
    private final TaskAccessPolicy accessPolicy;

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
        // BR-02: authorization is enforced here, not on the client. Sales Staff
        // are hard-scoped to their own tasks (any client-supplied assignedUserId
        // is ignored for them); Manager/Admin are unscoped and may filter by any
        // assignee via the request param.
        UserEntity currentUser = accessPolicy.currentUser();
        UUID scopedOwnerId = accessPolicy.listScopeOwnerId(currentUser);
        UUID effectiveAssignee = scopedOwnerId != null ? scopedOwnerId : parseUuid(assignedUserId);

        // Sort is defined by TaskSpecification.defaultSort() — pass unsorted Pageable.
        Specification<TaskEntity> spec = Specification.allOf(
                TaskSpecification.search(StringUtils.hasText(search) ? search.trim() : null),
                TaskSpecification.hasStatus(parseEnum(TaskStatus.class, status)),
                TaskSpecification.hasPriority(parseEnum(TaskPriority.class, priority)),
                TaskSpecification.assignedTo(effectiveAssignee),
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
