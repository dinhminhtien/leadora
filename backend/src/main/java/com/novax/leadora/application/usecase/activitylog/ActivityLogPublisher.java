package com.novax.leadora.application.usecase.activitylog;

import com.fasterxml.jackson.databind.JsonNode;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import org.slf4j.MDC;

import java.util.UUID;

public interface ActivityLogPublisher {

    void publish(ActivityLogCommand command);

    default void publish(
            ActivityLogType activityType,
            EntityType entityType,
            UUID entityId,
            String summary,
            JsonNode payload
    ) {
        publish(ActivityLogCommand.builder()
                .activityType(activityType)
                .entityType(entityType)
                .entityId(entityId)
                .summary(summary)
                .payload(payload)
                .correlationId(getCorrelationIdFromMdc())
                .build());
    }

    private UUID getCorrelationIdFromMdc() {
        String mdcVal = MDC.get("correlationId");
        if (mdcVal != null && !mdcVal.isBlank()) {
            try {
                return UUID.fromString(mdcVal.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
