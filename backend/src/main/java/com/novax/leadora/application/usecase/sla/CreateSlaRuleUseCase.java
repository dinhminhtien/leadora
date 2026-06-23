package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.request.SlaRuleRequest;
import com.novax.leadora.api.dto.response.SlaRuleResponse;
import com.novax.leadora.infrastructure.persistence.entity.SlaRuleEntity;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CreateSlaRuleUseCase {

    private final SlaRuleRepository slaRuleRepository;

    @Transactional
    public SlaRuleResponse execute(SlaRuleRequest request) {
        // E2: activity_type must be unique
        if (slaRuleRepository.existsByActivityType(request.getActivityType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SLA rule for activity type '" + request.getActivityType() + "' already exists. Use Update instead.");
        }

        // E3: warningThreshold must be less than deadlineHours
        if (request.getWarningThreshold() >= request.getDeadlineHours()) {
            throw new IllegalArgumentException(
                    "Warning threshold (" + request.getWarningThreshold() + "h) must be less than deadline ("
                    + request.getDeadlineHours() + "h)");
        }

        SlaRuleEntity rule = SlaRuleEntity.builder()
                .activityType(request.getActivityType())
                .name(request.getName())
                .deadlineHours(request.getDeadlineHours())
                .warningThreshold(request.getWarningThreshold())
                .escalationThreshold(request.getEscalationThreshold())
                .active(request.isActive())
                .build();

        return SlaRuleResponse.from(slaRuleRepository.save(rule));
    }
}
