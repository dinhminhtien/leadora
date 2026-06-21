package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveTaskUseCase {

    private final TaskRepository taskRepository;
    private final SlaTrackingRepository slaTrackingRepository;
    private final ReminderRepository reminderRepository;

    /**
     * UC-17.5: User marks an SLA-tracked task as resolved.
     * Updates task status → COMPLETED, resolves SLA tracking, and cancels pending reminders.
     *
     * @param taskId UUID of the task to resolve
     * @return updated TaskResponse
     */
    @Transactional
    public TaskResponse execute(UUID taskId) {
        TaskEntity task = taskRepository.findWithRelationsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        // E3: task already resolved
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task already resolved");
        }

        // Step 3-4 (POST-1): mark task COMPLETED
        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);

        // Step 4 (POST-2 via SLA): resolve SLA tracking records for this task
        List<SlaTrackingEntity> trackings =
                slaTrackingRepository.findByEntityTypeAndEntityId("TASK", taskId);
        int slaResolved = 0;
        for (SlaTrackingEntity tracking : trackings) {
            if (tracking.getStatus() != SlaStatus.RESOLVED) {
                tracking.setStatus(SlaStatus.RESOLVED);
                slaTrackingRepository.save(tracking);
                slaResolved++;
            }
        }

        // Step 5 (POST-2): cancel pending/overdue reminders linked to this task
        List<ReminderEntity> reminders =
                reminderRepository.findByRelatedEntityAndRelatedId("TASK", taskId);
        int remindersCancelled = 0;
        for (ReminderEntity reminder : reminders) {
            if (reminder.getStatus() == ReminderStatus.PENDING
                    || reminder.getStatus() == ReminderStatus.OVERDUE) {
                reminder.setStatus(ReminderStatus.CANCELLED);
                reminderRepository.save(reminder);
                remindersCancelled++;
            }
        }

        // POST-3: audit log
        log.info("Task resolved (UC-17.5): taskId={}, slaRecordsResolved={}, remindersCancelled={}",
                taskId, slaResolved, remindersCancelled);

        return TaskResponse.from(task);
    }
}
