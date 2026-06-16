package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.UpdateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateLeadUseCase {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    @Transactional
    public LeadResponse execute(UUID leadId, UpdateLeadRequest request) {
        LeadEntity lead = leadRepository.findWithUsersById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        if (StringUtils.hasText(request.getFullName())) {
            lead.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            lead.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            lead.setPhone(request.getPhone());
        }
        if (request.getCompanyName() != null) {
            lead.setCompanyName(request.getCompanyName());
        }
        if (request.getSource() != null) {
            lead.setSource(request.getSource());
        }
        if (request.getNotes() != null) {
            lead.setNotes(request.getNotes());
        }
        if (request.getStatus() != null) {
            LeadStatus newStatus = request.getStatus();
            lead.setStatus(newStatus);
            if (newStatus == LeadStatus.CONVERTED && lead.getConvertedAt() == null) {
                lead.setConvertedAt(OffsetDateTime.now());
            }
        }
        if (request.getAssignedUserId() != null) {
            UserEntity assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
            lead.setAssignedUser(assignedUser);
        }

        return LeadResponse.from(leadRepository.save(lead));
    }
}
