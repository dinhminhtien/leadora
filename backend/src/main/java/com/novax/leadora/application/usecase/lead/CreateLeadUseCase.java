package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateLeadUseCase {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    @Transactional
    public LeadResponse execute(CreateLeadRequest request) {
        UserEntity assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
        }

        LeadEntity lead = LeadEntity.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .companyName(request.getCompanyName())
                .address(request.getAddress())
                .isCorporate(Boolean.TRUE.equals(request.getIsCorporate()))
                .source(request.getSource())
                .status(LeadStatus.NEW)
                .notes(request.getNotes())
                .assignedUser(assignedUser)
                .build();

        LeadEntity saved = leadRepository.save(lead);
        return LeadResponse.from(saved);
    }
}
