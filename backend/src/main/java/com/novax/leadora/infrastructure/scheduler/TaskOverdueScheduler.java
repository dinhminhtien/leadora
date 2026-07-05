package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskOverdueScheduler {

    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void notifyOverdueTasks() {
        try {
            List<TaskEntity> overdue = taskRepository
                    .findByStatusAndEndAtBeforeAndOverdueNotifiedFalse(TaskStatus.OPEN, OffsetDateTime.now());

            for (TaskEntity task : overdue) {
                task.setOverdueNotified(true);
                taskRepository.save(task);

                if (task.getAssignedUser() != null) {
                    NotificationEntity notification = NotificationEntity.builder()
                            .user(task.getAssignedUser())
                            .type("TASK_OVERDUE")
                            .title("Task Overdue")
                            .message("Your task is overdue: \"" + task.getTitle() + "\"")
                            .relatedEntity("TASK")
                            .relatedId(task.getTaskId())
                            .build();
                    notificationRepository.save(notification);
                }
            }

            if (!overdue.isEmpty()) {
                log.info("Overdue task scan: {} task(s) flagged and notified", overdue.size());
            }
        } catch (Exception e) {
            log.error("Task overdue scheduler error: {}", e.getMessage(), e);
        }
    }
}
