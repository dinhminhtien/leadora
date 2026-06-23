package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteSlaRuleUseCase {

    private final SlaRuleRepository slaRuleRepository;
    private final SlaTrackingRepository slaTrackingRepository;

    @Transactional
    public void execute(UUID id) {
        if (!slaRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("SLA rule", id);
        }
        if (slaTrackingRepository.existsByRuleId(id)) {
            throw new BusinessRuleException(
                "Cannot delete SLA rule: it has associated tracking records. Deactivate it instead.");
        }
        slaRuleRepository.deleteById(id);
    }
}