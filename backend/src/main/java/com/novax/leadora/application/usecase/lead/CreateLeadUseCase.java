package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.DuplicateLeadException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateLeadUseCase {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public LeadResponse execute(CreateLeadRequest request) {
        // BR: an organization lead must name its company (also enforced client-side).
        if (Boolean.TRUE.equals(request.getIsCorporate()) && !StringUtils.hasText(request.getCompanyName())) {
            throw new IllegalArgumentException("Company name is required for an organization lead.");
        }

        // UC-8.1 duplicate detection — surface a clear 409 (with the existing lead id)
        // so the UI can warn and link to it, instead of silently creating a duplicate.
        assertNotDuplicate(request);

        UserEntity assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
        }

        // The creator owns the lead (drives SALES_STAFF owner-scoping). Non-fatal if unresolved.
        UserEntity creator = null;
        try {
            creator = currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve creator for new lead: {}", e.getMessage());
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
                .createdBy(creator)
                .build();

        LeadEntity saved = leadRepository.save(lead);

        // UC-17.2: start SLA tracking — non-fatal if no rule configured
        try {
            startSlaTrackingUseCase.execute("LEAD_RESPONSE", "LEAD", saved.getLeadId());
        } catch (Exception e) {
            log.warn("SLA tracking failed for lead {}: {}", saved.getLeadId(), e.getMessage());
        }

        return LeadResponse.from(saved);
    }

    /**
     * Rejects a create that would duplicate an existing lead's email or phone.
     * Email takes precedence (more reliable identity) and is matched case-insensitively.
     */
    private void assertNotDuplicate(CreateLeadRequest request) {
        if (StringUtils.hasText(request.getEmail())) {
            String email = request.getEmail().trim();
            leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                    .ifPresent(existing -> {
                        throw new DuplicateLeadException("email", email, existing.getLeadId());
                    });
        }
        if (StringUtils.hasText(request.getPhone())) {
            String phone = request.getPhone().trim();
            leadRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                    .ifPresent(existing -> {
                        throw new DuplicateLeadException("phone number", phone, existing.getLeadId());
                    });
        }
    }
}
