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
import java.util.List;

/**
 * UC-17.4: a still-unresolved breach that has also passed its escalationAt is pushed
 * to a higher tier (Admin) — distinct from the initial breach notification (which
 * already reaches Manager + assignee) so escalationAt is not just a stored-but-unused
 * timestamp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSlaEscalationUseCase {

    private final SlaTrackingRepository slaTrackingRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public int execute() {
        List<SlaTrackingEntity> toEscalate = slaTrackingRepository
                .findByStatusAndEscalationAtBeforeAndEscalationNotifiedFalse(SlaStatus.BREACHED, OffsetDateTime.now());

        if (toEscalate.isEmpty()) {
            return 0;
        }

        List<UserEntity> admins = userRepository.findByRoleName("ADMIN");

        for (SlaTrackingEntity tracking : toEscalate) {
            tracking.setEscalationNotified(true);
            slaTrackingRepository.save(tracking);

            String title = "SLA Escalation: " + SlaLabels.activityLabel(tracking.getActivityType());
            String message = String.format(
                    "%s SLA for %s remains unresolved beyond the escalation threshold. Escalated for review.",
                    SlaLabels.activityLabel(tracking.getActivityType()), SlaLabels.entityLabel(tracking.getEntityType()));

            for (UserEntity admin : admins) {
                notificationRepository.save(NotificationEntity.builder()
                        .user(admin)
                        .title(title)
                        .message(message)
                        .type("SLA_ESCALATED")
                        .relatedEntity("SLA")
                        .relatedId(tracking.getTrackingId())
                        .build());
            }

            log.warn("SLA escalated: trackingId={}, entityType={}, entityId={}, recipients={}",
                    tracking.getTrackingId(), tracking.getEntityType(), tracking.getEntityId(), admins.size());
        }

        return toEscalate.size();
    }
}
