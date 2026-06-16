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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        String searchParam = StringUtils.hasText(search) ? search.trim() : "";

        TaskStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = TaskStatus.valueOf(status.toUpperCase());
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

        return taskRepository.searchTasks(searchParam, statusParam, priorityParam, assignedUserIdParam, overdue, pageable)
                .map(TaskResponse::from);
    }
}
