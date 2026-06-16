package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetLeadDetailUseCase {

    private final LeadRepository leadRepository;

    @Transactional(readOnly = true)
    public LeadResponse execute(UUID leadId) {
        return leadRepository.findWithUsersById(leadId)
                .map(LeadResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
    }
}
