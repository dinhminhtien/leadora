package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityLogResponse(
        UUID id,
        ActorDto actor,
        String activityType,
        EntityDto entity,
        String summary,
        String reason,
        UUID correlationId,
        RecordOperation recordOperation,
        UUID refActivityId,
        OffsetDateTime createdAt,
        JsonNode payload
) {
    public record ActorDto(
            ActorType type,
            UUID id,
            String fullName,
            String role,
            String email
    ) {}

    public record EntityDto(
            String type,
            UUID id
    ) {}
}
