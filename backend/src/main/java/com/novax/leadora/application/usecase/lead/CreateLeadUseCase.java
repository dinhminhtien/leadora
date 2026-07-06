package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.DuplicateLeadException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
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
    private final NotificationRepository notificationRepository;

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

        // When a sales rep creates their own (quick) lead with no explicit assignee,
        // they own it: assign it to themselves so it shows in their "assigned to me"
        // list and its status can advance (an unassigned lead is locked at NEW until a
        // Manager assigns it). Managers/Admins still create unassigned drafts to distribute.
        if (assignedUser == null && creator != null && isSalesRep(creator)) {
            assignedUser = creator;
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

        // UC-15.1: notify the assigned sales rep, if the lead was created already assigned
        // by someone else. Skip self-assignment (a rep creating their own lead) — no point
        // telling them they were "assigned" a lead they just created.
        if (assignedUser != null && !assignedUser.equals(creator)) {
            notifyLeadAssigned(saved, assignedUser);
        }

        return LeadResponse.from(saved);
    }

    /** True when the user's role is a sales-rep role (owner-scoped), per the access policy. */
    private static boolean isSalesRep(UserEntity user) {
        String role = user.getRole() != null && user.getRole().getRoleName() != null
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
        return role.equals("SALES") || role.equals("SALES_STAFF");
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

    private void notifyLeadAssigned(LeadEntity lead, UserEntity assignedUser) {
        try {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(assignedUser)
                    .title("New Lead Assigned")
                    .message("You were assigned a new lead: " + lead.getFullName())
                    .type("LEAD_ASSIGNED")
                    .relatedEntity("LEAD")
                    .relatedId(lead.getLeadId())
                    .build();
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Lead-assigned notification failed for lead {}: {}", lead.getLeadId(), e.getMessage());
        }
    }
}
