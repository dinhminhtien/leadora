package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSlaBreachUseCase {

    private final SlaTrackingRepository slaTrackingRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SlaEntityResolver slaEntityResolver;

    /**
     * UC-17.4 step 1-2: Find all newly-breached ACTIVE tracking records,
     * update their status to BREACHED, and notify assigned staff + all managers.
     *
     * @return number of breach records processed
     */
    @Transactional
    public int execute() {
        List<SlaTrackingEntity> newBreaches = slaTrackingRepository
                .findByStatusAndDeadlineAtBefore(SlaStatus.ACTIVE, OffsetDateTime.now());

        if (newBreaches.isEmpty())
            return 0;

        // Load escalation targets once for the whole batch (BR-33)
        List<UserEntity> managers = userRepository.findAllWithRole().stream()
                .filter(u -> {
                    String role = u.getRole().getRoleName();
                    return "MANAGER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
                })
                .toList();

        for (SlaTrackingEntity tracking : newBreaches) {
            // POST-2: update DB status
            tracking.setStatus(SlaStatus.BREACHED);
            slaTrackingRepository.save(tracking);

            // Collect unique recipients: all managers + the directly assigned staff
            Set<UUID> seen = new HashSet<>();
            List<UserEntity> recipients = new ArrayList<>();
            for (UserEntity m : managers) {
                if (seen.add(m.getUserId()))
                    recipients.add(m);
            }
            UserEntity assigned = slaEntityResolver.resolveAssignedUser(tracking);
            if (assigned != null && seen.add(assigned.getUserId()))
                recipients.add(assigned);

            String title = "SLA Breach: " + SlaLabels.activityLabel(tracking.getActivityType());
            String message = String.format("%s SLA deadline exceeded for %s. Immediate action required.",
                    SlaLabels.activityLabel(tracking.getActivityType()), SlaLabels.entityLabel(tracking.getEntityType()));

            // POST-3: create notification for each recipient (UC-17.4 step 2)
            for (UserEntity recipient : recipients) {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(recipient)
                        .title(title)
                        .message(message)
                        .type("SLA_BREACH")
                        .relatedEntity("SLA")
                        .relatedId(tracking.getTrackingId())
                        .build();
                notificationRepository.save(notification);
            }

            log.warn("SLA breach: trackingId={}, entityType={}, entityId={}, recipients={}",
                    tracking.getTrackingId(), tracking.getEntityType(),
                    tracking.getEntityId(), recipients.size());
        }

        return newBreaches.size();
    }
}