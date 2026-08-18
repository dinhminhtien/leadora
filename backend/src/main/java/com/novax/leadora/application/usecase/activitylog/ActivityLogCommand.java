package com.novax.leadora.application.usecase.activitylog;

import com.fasterxml.jackson.databind.JsonNode;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogCommand {
    private ActorType actorType;
    private UUID actorUserId;
    private String actorRoleSnapshot;
    private ActivityLogType activityType;
    private EntityType entityType;
    private UUID entityId;
    private String summary;
    private JsonNode payload;
    private String reason;
    private UUID correlationId;
    private RecordOperation recordOperation;
    private UUID refActivityId;
}
