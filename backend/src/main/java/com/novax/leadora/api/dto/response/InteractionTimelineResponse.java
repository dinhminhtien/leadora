package com.novax.leadora.api.dto.response;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionTimelineResponse {
    private UUID id;
    private String type;         // "call", "email", "meeting", "note"
    private String description;
    private String agentName;    // User who logged the interaction
    private UUID agentId;
    private String linkedName;   // Resolved name of the linked lead/customer/deal
    private String linkedType;   // "lead", "customer", "deal"
    private UUID linkedId;       // ID of the linked lead/customer/deal
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;
}
