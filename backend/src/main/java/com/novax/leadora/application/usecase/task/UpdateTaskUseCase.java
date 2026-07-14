package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.request.UpdateTaskRequest;
import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityType;
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

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateTaskUseCase {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final TaskAccessPolicy accessPolicy;
    private final TaskNotifier taskNotifier;

    @Transactional
    public TaskResponse execute(UUID taskId, UpdateTaskRequest request) {
        TaskEntity task = taskRepository.findWithRelationsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        // BR-02: a Sales Staff may only update their own task (IDOR guard).
        UserEntity currentUser = accessPolicy.currentUser();
        accessPolicy.assertCanView(currentUser, task);

        // Snapshot the fields whose *transition* (not final value) drives a
        // notification — they are mutated in place further down.
        UserEntity previousAssignee = task.getAssignedUser();
        TaskStatus previousStatus = task.getStatus();

        if (StringUtils.hasText(request.getTitle())) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getResultNote() != null) {
            task.setResultNote(request.getResultNote());
        }

        // The activity type is now editable in its own right. Omitting it leaves the
        // task's current type alone — except on a row written before the backfill,
        // which carries none and is normalised to TASK on the way past, so an edit
        // never leaves a task without a type.
        if (StringUtils.hasText(request.getActivityType())) {
            task.setActivityType(ActivityType.fromWire(request.getActivityType()));
        } else if (task.getActivityType() == null) {
            task.setActivityType(ActivityType.DEFAULT);
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
            // BR-18: reassigning to a different user is a Manager/Admin action.
            UUID currentAssigneeId = previousAssignee != null ? previousAssignee.getUserId() : null;
            if (!request.getAssignedUserId().equals(currentAssigneeId)) {
                accessPolicy.assertFullAccess(currentUser);
            }
            UserEntity assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()));
            task.setAssignedUser(assignedUser);
        }

        // Re-pointing a task at a different lead/customer/deal rewrites its
        // business context, so it is a Manager/Admin action too. Re-sending the
        // *same* link is not a change — clients post the whole form back on every
        // edit, so only a genuine move is blocked.
        if (request.getLeadId() != null && changesLink(request.getLeadId(), currentLeadId(task))) {
            accessPolicy.assertFullAccess(currentUser);
            task.setLead(leadRepository.findById(request.getLeadId()).orElse(null));
        }
        if (request.getCustomerId() != null && changesLink(request.getCustomerId(), currentCustomerId(task))) {
            accessPolicy.assertFullAccess(currentUser);
            task.setCustomer(customerRepository.findById(request.getCustomerId()).orElse(null));
        }
        if (request.getDealId() != null && changesLink(request.getDealId(), currentDealId(task))) {
            accessPolicy.assertFullAccess(currentUser);
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

        TaskEntity saved = taskRepository.save(task);

        UserEntity newAssignee = saved.getAssignedUser();
        if (!sameUser(previousAssignee, newAssignee)) {
            taskNotifier.assigned(saved, newAssignee, currentUser);
            taskNotifier.reassignedAway(saved, previousAssignee, newAssignee, currentUser);
        }
        if (previousStatus != TaskStatus.COMPLETED && saved.getStatus() == TaskStatus.COMPLETED) {
            taskNotifier.completed(saved, currentUser);
        }

        return TaskResponse.from(saved);
    }

    /** True when the requested link points somewhere other than where the task points today. */
    private static boolean changesLink(UUID requested, UUID current) {
        return !requested.equals(current);
    }

    private static UUID currentLeadId(TaskEntity task) {
        return task.getLead() != null ? task.getLead().getLeadId() : null;
    }

    private static UUID currentCustomerId(TaskEntity task) {
        return task.getCustomer() != null ? task.getCustomer().getCustomerId() : null;
    }

    private static UUID currentDealId(TaskEntity task) {
        return task.getDeal() != null ? task.getDeal().getDealId() : null;
    }

    private static boolean sameUser(UserEntity a, UserEntity b) {
        UUID left = a != null ? a.getUserId() : null;
        UUID right = b != null ? b.getUserId() : null;
        return Objects.equals(left, right);
    }
}
