package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.api.dto.response.InteractionTimelineResponse;
import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InteractionTimelineMapper {

    public InteractionTimelineResponse mapToResponse(InteractTimelineEntity entity) {
        String agentName = entity.getUser() != null ? entity.getUser().getFullName() : "System";
        UUID agentId = entity.getUser() != null ? entity.getUser().getUserId() : null;

        String linkedName = "N/A";
        String linkedType = "N/A";
        UUID linkedId = null;

        if (entity.getDeal() != null) {
            linkedName = entity.getDeal().getDealName();
            linkedType = "deal";
            linkedId = entity.getDeal().getDealId();
        } else if (entity.getLead() != null) {
            linkedName = entity.getLead().getFullName();
            linkedType = "lead";
            linkedId = entity.getLead().getLeadId();
        } else if (entity.getCustomer() != null) {
            linkedName = entity.getCustomer().getFullName();
            linkedType = "customer";
            linkedId = entity.getCustomer().getCustomerId();
        }

        return InteractionTimelineResponse.builder()
                .id(entity.getInteractionId())
                .type(entity.getInteractionType())
                .description(entity.getDescription())
                .agentName(agentName)
                .agentId(agentId)
                .linkedName(linkedName)
                .linkedType(linkedType)
                .linkedId(linkedId)
                .occurredAt(entity.getOccurredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
