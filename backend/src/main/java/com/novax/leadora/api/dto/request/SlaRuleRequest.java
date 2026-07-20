package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SlaRuleRequest {

    @NotBlank(message = "Activity type is required")
    private String activityType;

    @NotBlank(message = "SLA rule name is required")
    private String name;

    @Min(value = 1, message = "Deadline must be at least 1 hour")
    @Max(value = 8760, message = "Deadline cannot exceed 8760 hours (1 year)")
    private int deadlineHours;

    @Min(value = 1, message = "Warning threshold must be at least 1 hour")
    @Max(value = 8760, message = "Warning threshold cannot exceed 8760 hours")
    private int warningThreshold;

    @Min(value = 1, message = "Escalation threshold must be at least 1 hour")
    @Max(value = 8760, message = "Escalation threshold cannot exceed 8760 hours")
    private int escalationThreshold;

    private boolean active;
}
