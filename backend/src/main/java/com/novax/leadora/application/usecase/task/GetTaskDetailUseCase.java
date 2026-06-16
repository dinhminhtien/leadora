package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTaskDetailUseCase {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public TaskResponse execute(UUID taskId) {
        return taskRepository.findWithRelationsById(taskId)
                .map(TaskResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }
}
