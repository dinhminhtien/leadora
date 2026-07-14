package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
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
public class OverdueReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void markOverdueReminders() {
        try {
            List<ReminderEntity> overdue = reminderRepository
                    .findByStatusAndRemindAtBefore(ReminderStatus.PENDING, OffsetDateTime.now());

            for (ReminderEntity reminder : overdue) {
                reminder.setStatus(ReminderStatus.OVERDUE);
                reminderRepository.save(reminder);

                if (reminder.getUser() != null) {
                    NotificationEntity notification = NotificationEntity.builder()
                            .user(reminder.getUser())
                            .type("REMINDER_OVERDUE")
                            .title("Reminder Overdue")
                            .message("Your reminder is overdue: \"" + reminder.getTitle() + "\"")
                            .relatedEntity("REMINDER")
                            .relatedId(reminder.getReminderId())
                            .build();
                    notificationRepository.save(notification);
                }
            }

            if (!overdue.isEmpty()) {
                log.info("Overdue reminder scan: {} reminder(s) marked OVERDUE", overdue.size());
            }
        } catch (Exception e) {
            log.error("Overdue reminder scheduler error: {}", e.getMessage(), e);
        }
    }
}
