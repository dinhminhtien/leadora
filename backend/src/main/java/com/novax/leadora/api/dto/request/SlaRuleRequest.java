package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SlaRuleRequest {

    @NotBlank
    private String activityType;

    @NotBlank
    private String name;

    @Min(1)
    private int deadlineHours;

    @Min(1)
    private int warningThreshold;

    @Min(1)
    private int escalationThreshold;

    private boolean active;
}
