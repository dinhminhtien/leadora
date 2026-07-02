package com.novax.leadora.application.usecase.reminder;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscalateReminderUseCase {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public void execute(UUID reminderId) {
        ReminderEntity reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", reminderId));

        if (reminder.getStatus() == ReminderStatus.DONE) {
            throw new BusinessException("REMINDER_ALREADY_DONE", "Already completed", HttpStatus.CONFLICT);
        }

        // Only OVERDUE reminders warrant escalation
        if (reminder.getStatus() != ReminderStatus.OVERDUE) {
            throw new BusinessException("NOT_OVERDUE",
                    "Only overdue reminders can be escalated to a manager", HttpStatus.CONFLICT);
        }

        // Resolve escalating user from JWT
        UUID escalatedByUserId = currentUserProvider.resolve(null).getUserId();
        UserEntity escalatedBy = userRepository.findById(escalatedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", escalatedByUserId));

        List<UserEntity> managers = userRepository.findByRoleName("MANAGER");

        String message = escalatedBy.getFullName() + " escalated: \"" + reminder.getTitle() + "\"";

        for (UserEntity manager : managers) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(manager)
                    .type("REMINDER_ESCALATED")
                    .title("Reminder Escalated")
                    .message(message)
                    .relatedEntity("REMINDER")
                    .relatedId(reminderId)
                    .build();
            notificationRepository.save(notification);
        }

        log.info("Reminder escalated: id={} by userId={}, notified {} manager(s)",
                reminderId, escalatedByUserId, managers.size());
    }
}
