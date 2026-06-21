package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.response.SlaRuleResponse;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSlaRulesUseCase {

    private final SlaRuleRepository slaRuleRepository;

    @Transactional(readOnly = true)
    public List<SlaRuleResponse> execute() {
        return slaRuleRepository.findAllByOrderByActivityTypeAsc()
                .stream()
                .map(SlaRuleResponse::from)
                .toList();
    }
}
