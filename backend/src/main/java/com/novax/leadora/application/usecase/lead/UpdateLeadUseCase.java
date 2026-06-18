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
import java.util.Map;
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
        if (request.getStatus() != null) {
            LeadStatus newStatus = request.getStatus();
            validateStatusTransition(lead.getStatus(), newStatus);
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
