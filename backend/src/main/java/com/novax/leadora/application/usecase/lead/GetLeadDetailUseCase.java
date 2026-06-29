package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetLeadDetailUseCase {

    private final LeadRepository leadRepository;
    private final LeadAccessPolicy leadAccessPolicy;

    @Transactional(readOnly = true)
    public LeadResponse execute(UUID leadId) {
        LeadEntity lead = leadRepository.findWithUsersById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        // UC-8.3 RBAC: a Sales Staff may only open their own leads; others per policy.
        leadAccessPolicy.assertCanView(leadAccessPolicy.currentUser(), lead);

        return LeadResponse.from(lead);
    }
}
