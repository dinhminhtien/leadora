package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.UpdateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
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
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final NotificationRepository notificationRepository;
    private final LeadAccessPolicy leadAccessPolicy;

    @Transactional
    public LeadResponse execute(UUID leadId, UpdateLeadRequest request) {
        LeadEntity lead = leadRepository.findWithUsersById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        // UC-8.4 RBAC: same owner-scoping as viewing — a Sales Staff may only
        // modify leads assigned to (or created by) them; MANAGER/ADMIN unscoped.
        leadAccessPolicy.assertCanView(leadAccessPolicy.currentUser(), lead);

        // BR-08: after conversion the lead is an immutable historical snapshot —
        // reject any edit so the record kept for history/audit can't be rewritten.
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new IllegalStateException(
                    "This lead has been converted and is now a locked historical record; it can no longer be edited.");
        }

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
        if (request.getInterestedService() != null) {
            lead.setInterestedService(request.getInterestedService());
        }
        if (request.getNotes() != null) {
            lead.setNotes(request.getNotes());
        }
        // Apply (re)assignment BEFORE the status check, so assigning and advancing a
        // lead in the same request is allowed (the assignee is already set when the
        // status guard below runs).
        if (request.getAssignedUserId() != null) {
            UUID previousAssigneeId = lead.getAssignedUser() != null ? lead.getAssignedUser().getUserId() : null;
            // An unknown assignee must fail loudly (404) — falling back to null would
            // silently UNASSIGN the lead (and orphan its running SLA tracking).
            UserEntity assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()));
            lead.setAssignedUser(assignedUser);

            if (!assignedUser.getUserId().equals(previousAssigneeId)) {
                // UC-15.1: notify the newly (re)assigned sales rep
                notifyLeadAssigned(lead, assignedUser);

                // BR-06: SLA enforcement begins on the FIRST assignment (a draft lead
                // started none at creation). A reassignment (previous owner non-null)
                // does not restart tracking.
                if (previousAssigneeId == null) {
                    try {
                        startSlaTrackingUseCase.execute("LEAD_RESPONSE", "LEAD", lead.getLeadId());
                    } catch (Exception e) {
                        log.warn("SLA tracking start-on-assignment failed for lead {}: {}",
                                lead.getLeadId(), e.getMessage());
                    }
                }
            }
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
            // BR-05: entering active follow-up (leaving NEW toward CONTACTED/QUALIFIED)
            // requires the qualifying details — a phone or email to reach the lead, the
            // inquiry source, and the interested service — not just an assignee. A lead
            // may still be marked LOST without them.
            if (previousStatus == LeadStatus.NEW
                    && newStatus != LeadStatus.NEW
                    && newStatus != LeadStatus.LOST) {
                if (!StringUtils.hasText(lead.getEmail()) && !StringUtils.hasText(lead.getPhone())) {
                    throw new IllegalStateException(
                            "A phone number or email is required before a lead enters active follow-up.");
                }
                if (!StringUtils.hasText(lead.getSource())) {
                    throw new IllegalStateException(
                            "The inquiry source is required before a lead enters active follow-up.");
                }
                if (!StringUtils.hasText(lead.getInterestedService())) {
                    throw new IllegalStateException(
                            "The interested service is required before a lead enters active follow-up.");
                }
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

    private void notifyLeadAssigned(LeadEntity lead, UserEntity assignedUser) {
        try {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(assignedUser)
                    .title("Lead Assigned")
                    .message("You were assigned the lead: " + lead.getFullName())
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
