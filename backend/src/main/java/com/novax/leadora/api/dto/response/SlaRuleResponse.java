package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.SlaRuleEntity;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SlaRuleResponse {

    private UUID id;
    private String activityType;
    private String name;
    private int deadlineHours;
    private int warningThreshold;
    private int escalationThreshold;
    private boolean active;

    public static SlaRuleResponse from(SlaRuleEntity entity) {
        return SlaRuleResponse.builder()
                .id(entity.getRuleId())
                .activityType(entity.getActivityType())
                .name(entity.getName())
                .deadlineHours(entity.getDeadlineHours())
                .warningThreshold(entity.getWarningThreshold())
                .escalationThreshold(entity.getEscalationThreshold())
                .active(entity.isActive())
                .build();
    }
}
