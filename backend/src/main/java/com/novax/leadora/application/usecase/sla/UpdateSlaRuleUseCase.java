package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.request.SlaRuleRequest;
import com.novax.leadora.api.dto.response.SlaRuleResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SlaRuleEntity;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateSlaRuleUseCase {

    private final SlaRuleRepository slaRuleRepository;

    @Transactional
    public SlaRuleResponse execute(UUID ruleId, SlaRuleRequest request) {
        // E3: warningThreshold must be less than deadlineHours
        if (request.getWarningThreshold() >= request.getDeadlineHours()) {
            throw new IllegalArgumentException(
                    "Warning threshold (" + request.getWarningThreshold() + "h) must be less than deadline ("
                    + request.getDeadlineHours() + "h)");
        }

        SlaRuleEntity rule = slaRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("SlaRule", ruleId));

        rule.setActivityType(request.getActivityType());
        rule.setName(request.getName());
        rule.setDeadlineHours(request.getDeadlineHours());
        rule.setWarningThreshold(request.getWarningThreshold());
        rule.setEscalationThreshold(request.getEscalationThreshold());
        rule.setActive(request.isActive());

        return SlaRuleResponse.from(slaRuleRepository.save(rule));
    }
}
