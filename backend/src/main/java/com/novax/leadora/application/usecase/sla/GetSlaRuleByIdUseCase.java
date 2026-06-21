package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.response.SlaRuleResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSlaRuleByIdUseCase {

    private final SlaRuleRepository slaRuleRepository;

    @Transactional(readOnly = true)
    public SlaRuleResponse execute(UUID ruleId) {
        return slaRuleRepository.findById(ruleId)
                .map(SlaRuleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("SlaRule", ruleId));
    }
}
