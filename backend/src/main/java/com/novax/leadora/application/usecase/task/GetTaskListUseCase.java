package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            boolean overdue,
            int page,
            int size
    ) {
        // Sort is handled inside the JPQL query (startAt ASC NULLS LAST, then createdAt DESC).
        // Pass an unsorted Pageable so the DB-level ORDER BY is not overridden.
        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());

        String searchParam = StringUtils.hasText(search) ? search.trim() : "";

        boolean overdueParam = overdue;
        TaskStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                TaskStatus parsedStatus = TaskStatus.valueOf(status.toUpperCase());
                if (parsedStatus == TaskStatus.OVERDUE) {
                    overdueParam = true;
                } else {
                    statusParam = parsedStatus;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        TaskPriority priorityParam = null;
        if (StringUtils.hasText(priority)) {
            try {
                priorityParam = TaskPriority.valueOf(priority.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        UUID assignedUserIdParam = null;
        if (StringUtils.hasText(assignedUserId)) {
            try {
                assignedUserIdParam = UUID.fromString(assignedUserId);
            } catch (IllegalArgumentException ignored) {}
        }

        return taskRepository.searchTasks(searchParam, statusParam, priorityParam, assignedUserIdParam, overdueParam, pageable)
                .map(TaskResponse::from);
    }
}
