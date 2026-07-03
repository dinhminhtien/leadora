package com.novax.leadora.api.dto.response;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionAuditLogResponse {
    private UUID auditId;
    private String action; // CREATED, UPDATED
    private String changedByName;
    private String changedByRole;
    private OffsetDateTime timestamp;
    private String fieldName; // null for CREATED
    private String oldValue;
    private String newValue;
}
