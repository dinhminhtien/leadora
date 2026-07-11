package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTaskDetailUseCase {

    private final TaskRepository taskRepository;
    private final TaskAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public TaskResponse execute(UUID taskId) {
        TaskEntity task = taskRepository.findWithRelationsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        // BR-02: a Sales Staff may only open a task assigned to them (IDOR guard).
        UserEntity currentUser = accessPolicy.currentUser();
        accessPolicy.assertCanView(currentUser, task);

        // fromDetail adds the linked record's business context; the extra
        // deal.customer / deal.owner / lead.owner reads are lazy but the
        // read-only transaction keeps the session open for this single task.
        return TaskResponse.fromDetail(task);
    }
}
