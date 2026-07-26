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
public class ProcessSlaWarningUseCase {

    private final SlaTrackingRepository slaTrackingRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SlaEntityResolver slaEntityResolver;

    /**
     * UC-17.2 step 5-6: Find ACTIVE records whose warningAt has passed but warning
     * notification has not been sent yet. Notify assigned staff + all managers.
     *
     * @return number of warning notifications processed
     */
    @Transactional
    public int execute() {
        List<SlaTrackingEntity> pendingWarnings = slaTrackingRepository
                .findByStatusAndWarningAtBeforeAndWarningNotifiedFalse(SlaStatus.ACTIVE, OffsetDateTime.now());

        if (pendingWarnings.isEmpty())
            return 0;

        List<UserEntity> managers = userRepository.findAllWithRole().stream()
                .filter(u -> {
                    String role = u.getRole().getRoleName();
                    return "MANAGER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
                })
                .toList();

        for (SlaTrackingEntity tracking : pendingWarnings) {
            tracking.setWarningNotified(true);
            slaTrackingRepository.save(tracking);

            Set<UUID> seen = new HashSet<>();
            List<UserEntity> recipients = new ArrayList<>();
            for (UserEntity m : managers) {
                if (seen.add(m.getUserId()))
                    recipients.add(m);
            }
            UserEntity assigned = slaEntityResolver.resolveAssignedUser(tracking);
            if (assigned != null && seen.add(assigned.getUserId()))
                recipients.add(assigned);

            String title = "SLA Warning: " + SlaLabels.activityLabel(tracking.getActivityType());
            String message = String.format(
                    "%s SLA deadline is approaching for %s. Take action now to avoid a breach.",
                    SlaLabels.activityLabel(tracking.getActivityType()),
                    SlaLabels.entityLabel(tracking.getEntityType()));

            for (UserEntity recipient : recipients) {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(recipient)
                        .title(title)
                        .message(message)
                        .type("SLA_WARNING")
                        .relatedEntity("SLA")
                        .relatedId(tracking.getTrackingId())
                        .build();
                notificationRepository.save(notification);
            }

            log.warn("SLA warning sent: trackingId={}, entityType={}, entityId={}, recipients={}",
                    tracking.getTrackingId(), tracking.getEntityType(),
                    tracking.getEntityId(), recipients.size());
        }

        return pendingWarnings.size();
    }
}