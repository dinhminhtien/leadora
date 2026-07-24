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

/**
 * BR-34 (first half): send a "due soon" heads-up before a reminder's deadline, on the
 * same cadence as {@link OverdueReminderScheduler} (which covers the "after deadline" half).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreDueReminderScheduler {

    private static final int LOOKAHEAD_HOURS = 1;

    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void notifyDueSoon() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            List<ReminderEntity> dueSoon = reminderRepository.findByStatusAndPreDueNotifiedFalseAndRemindAtBetween(
                    ReminderStatus.PENDING, now, now.plusHours(LOOKAHEAD_HOURS));

            for (ReminderEntity reminder : dueSoon) {
                reminder.setPreDueNotified(true);
                reminderRepository.save(reminder);

                if (reminder.getUser() != null) {
                    NotificationEntity notification = NotificationEntity.builder()
                            .user(reminder.getUser())
                            .type("REMINDER_DUE_SOON")
                            .title("Reminder Due Soon")
                            .message("Reminder due soon: \"" + reminder.getTitle() + "\"")
                            .relatedEntity("REMINDER")
                            .relatedId(reminder.getReminderId())
                            .build();
                    notificationRepository.save(notification);
                }
            }

            if (!dueSoon.isEmpty()) {
                log.info("Pre-due reminder scan: {} reminder(s) notified", dueSoon.size());
            }
        } catch (Exception e) {
            log.error("Pre-due reminder scheduler error: {}", e.getMessage(), e);
        }
    }
}
