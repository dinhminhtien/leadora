package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Data
@Builder
public class SlaMonitoringResponse {

    private UUID trackingId;
    private String entityType;
    private UUID entityId;
    private String activityType;
    private OffsetDateTime startedAt;
    private OffsetDateTime deadlineAt;
    private OffsetDateTime warningAt;

    /** Computed at query time: WITHIN_SLA | WARNING | BREACHED */
    private String displayStatus;

    /** Positive = hours left; negative = hours overdue */
    private long hoursRemaining;

    public static SlaMonitoringResponse from(SlaTrackingEntity entity, OffsetDateTime now) {
        String displayStatus = computeDisplayStatus(entity, now);
        long hoursRemaining = ChronoUnit.HOURS.between(now, entity.getDeadlineAt());

        return SlaMonitoringResponse.builder()
                .trackingId(entity.getTrackingId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .activityType(entity.getActivityType())
                .startedAt(entity.getStartedAt())
                .deadlineAt(entity.getDeadlineAt())
                .warningAt(entity.getWarningAt())
                .displayStatus(displayStatus)
                .hoursRemaining(hoursRemaining)
                .build();
    }

    private static String computeDisplayStatus(SlaTrackingEntity entity, OffsetDateTime now) {
        if (SlaStatus.BREACHED.equals(entity.getStatus()) || !now.isBefore(entity.getDeadlineAt())) {
            return "BREACHED";
        }
        if (!now.isBefore(entity.getWarningAt())) {
            return "WARNING";
        }
        return "WITHIN_SLA";
    }
}
