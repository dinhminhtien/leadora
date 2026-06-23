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
    private OffsetDateTime resolvedAt;

    /** ACTIVE | BREACHED | RESOLVED */
    private String slaStatus;

    /** Computed: WITHIN_SLA | WARNING | BREACHED — for RESOLVED records, reflects performance at resolve time */
    private String displayStatus;

    /** For active: hours until deadline (negative = overdue). For resolved: hours between resolvedAt and deadline. */
    private long hoursRemaining;

    public static SlaMonitoringResponse from(SlaTrackingEntity entity, OffsetDateTime now) {
        String displayStatus = computeDisplayStatus(entity, now);
        OffsetDateTime ref = SlaStatus.RESOLVED.equals(entity.getStatus()) && entity.getResolvedAt() != null
                ? entity.getResolvedAt()
                : now;
        long hoursRemaining = ChronoUnit.HOURS.between(ref, entity.getDeadlineAt());

        return SlaMonitoringResponse.builder()
                .trackingId(entity.getTrackingId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .activityType(entity.getActivityType())
                .startedAt(entity.getStartedAt())
                .deadlineAt(entity.getDeadlineAt())
                .warningAt(entity.getWarningAt())
                .resolvedAt(entity.getResolvedAt())
                .slaStatus(entity.getStatus().name())
                .displayStatus(displayStatus)
                .hoursRemaining(hoursRemaining)
                .build();
    }

    private static String computeDisplayStatus(SlaTrackingEntity entity, OffsetDateTime now) {
        // For resolved records, evaluate performance at the time of resolution
        if (SlaStatus.RESOLVED.equals(entity.getStatus()) && entity.getResolvedAt() != null) {
            OffsetDateTime ref = entity.getResolvedAt();
            if (!ref.isBefore(entity.getDeadlineAt())) return "BREACHED";
            if (!ref.isBefore(entity.getWarningAt())) return "WARNING";
            return "WITHIN_SLA";
        }
        if (SlaStatus.BREACHED.equals(entity.getStatus()) || !now.isBefore(entity.getDeadlineAt())) {
            return "BREACHED";
        }
        if (!now.isBefore(entity.getWarningAt())) {
            return "WARNING";
        }
        return "WITHIN_SLA";
    }
}
