package com.novax.leadora.application.usecase.task;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Task lifecycle notifications (UC-10.x).
 *
 * <p>Writes to the shared {@code notifications} table that
 * {@code GET /api/v1/notifications} already serves; web and mobile deep-link on
 * {@code relatedEntity=TASK} + {@code relatedId}, so no client wiring is needed
 * beyond emitting the row.
 *
 * <p>Emission is best-effort by design: a notification must never roll back the
 * business transaction that triggered it, so every write is guarded — the same
 * contract {@code CreateLeadUseCase} uses for lead assignment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotifier {

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final NotificationRepository notificationRepository;

    /**
     * Somebody was handed a task to work on — on create, on an update that moves
     * the assignee, and on the new task a resign produces.
     */
    public void assigned(TaskEntity task, UserEntity assignee, UserEntity actor) {
        // Working on your own task is not news; only a hand-off is.
        if (isSameUser(assignee, actor)) {
            return;
        }
        send(task, assignee, "TASK_ASSIGNED", "New Task Assigned",
                "%s assigned you a task: \"%s\".%s"
                        .formatted(displayName(actor), task.getTitle(), dueSuffix(task)));
    }

    /**
     * The previous assignee is off the hook once a manager hands their task to
     * someone else.
     */
    public void reassignedAway(TaskEntity task, UserEntity previousAssignee, UserEntity newAssignee,
                               UserEntity actor) {
        if (isSameUser(previousAssignee, newAssignee) || isSameUser(previousAssignee, actor)) {
            return;
        }
        send(task, previousAssignee, "TASK_REASSIGNED", "Task Reassigned",
                "\"%s\" has been reassigned to %s. No further action is required."
                        .formatted(task.getTitle(), displayName(newAssignee)));
    }

    /**
     * The creator hears back when somebody else closes the task they raised.
     */
    public void completed(TaskEntity task, UserEntity actor) {
        UserEntity creator = task.getCreatedBy();
        if (creator == null || isSameUser(creator, actor)) {
            return;
        }
        send(task, creator, "TASK_COMPLETED", "Task Completed",
                "%s completed the task you created: \"%s\"."
                        .formatted(displayName(actor), task.getTitle()));
    }

    private void send(TaskEntity task, UserEntity recipient, String type, String title, String message) {
        if (recipient == null) {
            return;
        }
        try {
            notificationRepository.save(NotificationEntity.builder()
                    .user(recipient)
                    .title(title)
                    .message(message)
                    .type(type)
                    .relatedEntity("TASK")
                    .relatedId(task.getTaskId())
                    .build());
        } catch (Exception e) {
            log.warn("{} notification failed for task {}: {}", type, task.getTaskId(), e.getMessage());
        }
    }

    private static boolean isSameUser(UserEntity a, UserEntity b) {
        return a != null && b != null && Objects.equals(a.getUserId(), b.getUserId());
    }

    private static String displayName(UserEntity user) {
        return user != null && user.getFullName() != null ? user.getFullName() : "A teammate";
    }

    /** " Due 12 Jul 2026, 16:00." — omitted entirely when the task has no end time. */
    private static String dueSuffix(TaskEntity task) {
        return task.getEndAt() == null ? "" : " Due " + DUE_FORMAT.format(task.getEndAt()) + ".";
    }
}
