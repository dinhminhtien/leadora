package com.novax.leadora.api.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class UpdateTaskRequest {

    private String title;
    private String description;
    private UUID assignedUserId;
    private String priority;
    private String status;

    /**
     * CALL / EMAIL / MEETING / SITE_VISIT / FOLLOW_UP / TASK. Optional, like every
     * field here — omitting it leaves the task's current type alone (except on a
     * legacy row that has none, which is normalised to TASK).
     */
    private String activityType;
    private String resultNote;
    private UUID leadId;
    private UUID customerId;
    private UUID dealId;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    private String primaryContactName;
    private String primaryContactPhone;
}
