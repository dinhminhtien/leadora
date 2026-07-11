package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.request.ResignTaskRequest;
import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UC-10.7: Resign Task
 *
 * Resign behavior:
 * 1. Fetch the original task
 * 2. Clone it with updated details (startAt, endAt, assignee, notes)
 * 3. Create a new task with resultNote stamped with a soft parent reference
 * 4. Do NOT modify the original task (preserve history)
 * 5. Return the newly created task
 *
 * This ensures:
 * - Original task remains unchanged
 * - Full audit trail is preserved
 * - Follow-up chain is maintained
 * - New task can be tracked as a follow-up
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResignTaskUseCase {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskAccessPolicy accessPolicy;

    @Transactional
    public TaskResponse execute(UUID originalTaskId, ResignTaskRequest request) {
        // [1] Fetch the original task
        TaskEntity originalTask = taskRepository.findWithRelationsById(originalTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", originalTaskId));

        // [2] Authorization. BR-02: must own the task (or be a manager) to touch
        // it at all. BR-18: reassigning to a *different* user is Manager/Admin
        // only — an owner may reschedule their own task but not hand it off.
        UserEntity currentUser = accessPolicy.currentUser();
        accessPolicy.assertCanView(currentUser, originalTask);

        UserEntity assignedUser = originalTask.getAssignedUser();
        if (request.getAssignedUserId() != null) {
            UUID currentAssigneeId = assignedUser != null ? assignedUser.getUserId() : null;
            if (!request.getAssignedUserId().equals(currentAssigneeId)) {
                accessPolicy.assertFullAccess(currentUser);
            }
            assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()));
        }

        // [3] Resolve priority (use request value or keep original)
        TaskPriority priority = originalTask.getPriority();
        if (StringUtils.hasText(request.getPriority())) {
            try {
                priority = TaskPriority.valueOf(request.getPriority().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        // [4] Build soft parent reference for audit trail (no DB column needed)
        String parentRef = "Follow-up from task: " + originalTaskId;
        String resultNote = (request.getResignNote() != null && !request.getResignNote().isBlank())
                ? request.getResignNote() + "\n[" + parentRef + "]"
                : "[" + parentRef + "]";

        // [5] Build the new task with cloned data
        TaskEntity resignedTask = TaskEntity.builder()
                .title(request.getTitle() != null ? request.getTitle() : originalTask.getTitle())
                .description(request.getDescription() != null ? request.getDescription() : originalTask.getDescription())
                .priority(priority)
                .status(TaskStatus.OPEN)
                .resultNote(resultNote)
                .assignedUser(assignedUser)
                .lead(originalTask.getLead())
                .customer(originalTask.getCustomer())
                .deal(originalTask.getDeal())
                .startAt(request.getStartAt() != null ? request.getStartAt() : OffsetDateTime.now())
                .endAt(request.getEndAt())
                .primaryContactName(originalTask.getPrimaryContactName())
                .primaryContactPhone(originalTask.getPrimaryContactPhone())
                .build();

        // [6] Save the new task
        TaskEntity saved = taskRepository.save(resignedTask);

        log.info("Task resigned: original taskId={}, new taskId={}, assignedTo={}", 
                originalTaskId, saved.getTaskId(), assignedUser.getFullName());

        return TaskResponse.from(saved);
    }
}
