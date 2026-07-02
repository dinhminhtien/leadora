package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.UpdateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateLeadUseCase {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;

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
        if (request.getAddress() != null) {
            lead.setAddress(request.getAddress());
        }
        if (request.getIsCorporate() != null) {
            lead.setIsCorporate(request.getIsCorporate());
        }
        if (request.getSource() != null) {
            lead.setSource(request.getSource());
        }
        if (request.getNotes() != null) {
            lead.setNotes(request.getNotes());
        }
        // Apply (re)assignment BEFORE the status check, so assigning and advancing a
        // lead in the same request is allowed (the assignee is already set when the
        // status guard below runs).
        if (request.getAssignedUserId() != null) {
            UserEntity assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
            lead.setAssignedUser(assignedUser);
        }
        if (request.getStatus() != null) {
            LeadStatus newStatus = request.getStatus();
            LeadStatus previousStatus = lead.getStatus();
            // BR: an unassigned lead is a draft — it stays NEW until a Manager assigns it
            // to a sales rep. Only then can its status move through the pipeline.
            if (newStatus != previousStatus && lead.getAssignedUser() == null) {
                throw new IllegalStateException(
                        "Lead must be assigned to a sales rep before its status can change.");
            }
            validateStatusTransition(previousStatus, newStatus);
            lead.setStatus(newStatus);
            if (newStatus == LeadStatus.CONVERTED && lead.getConvertedAt() == null) {
                lead.setConvertedAt(OffsetDateTime.now());
            }

            // UC-17.2: leaving NEW means the sales rep has responded to the lead — auto-resolve
            // the LEAD_RESPONSE SLA tracking. Non-fatal if no rule was ever configured.
            if (previousStatus == LeadStatus.NEW && newStatus != LeadStatus.NEW) {
                try {
                    resolveSlaBreachUseCase.executeByEntity("LEAD", lead.getLeadId());
                } catch (Exception e) {
                    log.warn("SLA auto-resolve failed for lead {}: {}", lead.getLeadId(), e.getMessage());
                }
            }
        }

        // BR: an organization lead must name its company. Validate the resulting state,
        // since either isCorporate or companyName may have just changed.
        if (Boolean.TRUE.equals(lead.getIsCorporate()) && !StringUtils.hasText(lead.getCompanyName())) {
            throw new IllegalArgumentException("Company name is required for an organization lead.");
        }

        return LeadResponse.from(leadRepository.save(lead));
    }

    /**
     * One-directional lead lifecycle: NEW → CONTACTED → QUALIFIED.
     * A lead may only advance a single stage at a time (no skipping, no going back).
     * An active lead can always be marked LOST. CONVERTED is reached only through the
     * conversion flow (ConvertLeadUseCase), and both CONVERTED and LOST are terminal.
     */
    private static final Map<LeadStatus, LeadStatus> FORWARD = Map.of(
            LeadStatus.NEW, LeadStatus.CONTACTED,
            LeadStatus.CONTACTED, LeadStatus.QUALIFIED
    );

    private void validateStatusTransition(LeadStatus current, LeadStatus next) {
        if (current == next) {
            return; // no change
        }
        if (current == LeadStatus.CONVERTED) {
            throw new IllegalStateException("A converted lead's status is locked and cannot be changed.");
        }
        if (current == LeadStatus.LOST) {
            throw new IllegalStateException("A lost lead's status can no longer be changed.");
        }
        if (next == LeadStatus.CONVERTED) {
            throw new IllegalStateException(
                    "A lead can only become CONVERTED through the conversion flow, not a status update.");
        }
        if (next == LeadStatus.LOST) {
            return; // an active lead can always be marked Lost
        }
        if (FORWARD.get(current) != next) {
            throw new IllegalStateException(
                    "Invalid status transition: " + current + " → " + next +
                    ". Leads move forward one stage at a time (NEW → CONTACTED → QUALIFIED).");
        }
    }
}
