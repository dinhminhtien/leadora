package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.UpdateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.DuplicateLeadException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

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
            throw new BusinessException("LEAD_LOCKED",
                    "This lead has been converted and is now a locked historical record; "
                            + "it can no longer be edited.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Creation refuses a lead whose email or phone already exists (UC-8.1), but editing did
        // not — so the same collision could simply be typed in afterwards, which left duplicate
        // detection guarding only the front door.
        assertNotDuplicate(request, lead);

        ObjectNode updatePayload = objectMapper.createObjectNode();
        boolean detailsChanged = false;

        if (StringUtils.hasText(request.getFullName()) && !request.getFullName().equals(lead.getFullName())) {
            updatePayload.put("previousFullName", lead.getFullName());
            updatePayload.put("newFullName", request.getFullName());
            lead.setFullName(request.getFullName());
            detailsChanged = true;
        }
        if (request.getEmail() != null && !request.getEmail().equals(lead.getEmail())) {
            updatePayload.put("previousEmail", lead.getEmail());
            updatePayload.put("newEmail", request.getEmail());
            lead.setEmail(request.getEmail());
            detailsChanged = true;
        }
        if (request.getPhone() != null && !request.getPhone().equals(lead.getPhone())) {
            updatePayload.put("previousPhone", lead.getPhone());
            updatePayload.put("newPhone", request.getPhone());
            lead.setPhone(request.getPhone());
            detailsChanged = true;
        }
        if (request.getCompanyName() != null && !request.getCompanyName().equals(lead.getCompanyName())) {
            updatePayload.put("previousCompanyName", lead.getCompanyName());
            updatePayload.put("newCompanyName", request.getCompanyName());
            lead.setCompanyName(request.getCompanyName());
            detailsChanged = true;
        }
        if (request.getAddress() != null && !request.getAddress().equals(lead.getAddress())) {
            updatePayload.put("previousAddress", lead.getAddress());
            updatePayload.put("newAddress", request.getAddress());
            lead.setAddress(request.getAddress());
            detailsChanged = true;
        }
        if (request.getIsCorporate() != null && !request.getIsCorporate().equals(lead.getIsCorporate())) {
            updatePayload.put("previousIsCorporate", lead.getIsCorporate());
            updatePayload.put("newIsCorporate", request.getIsCorporate());
            lead.setIsCorporate(request.getIsCorporate());
            detailsChanged = true;
        }
        if (request.getSource() != null && !request.getSource().equals(lead.getSource())) {
            updatePayload.put("previousSource", lead.getSource());
            updatePayload.put("newSource", request.getSource());
            lead.setSource(request.getSource());
            detailsChanged = true;
        }
        if (request.getInterestedService() != null && !request.getInterestedService().equals(lead.getInterestedService())) {
            updatePayload.put("previousInterestedService", lead.getInterestedService());
            updatePayload.put("newInterestedService", request.getInterestedService());
            lead.setInterestedService(request.getInterestedService());
            detailsChanged = true;
        }
        if (request.getNotes() != null && !request.getNotes().equals(lead.getNotes())) {
            updatePayload.put("previousNotes", lead.getNotes());
            updatePayload.put("newNotes", request.getNotes());
            lead.setNotes(request.getNotes());
            detailsChanged = true;
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
                updatePayload.put("previousAssignedUserId", previousAssigneeId != null ? previousAssigneeId.toString() : null);
                updatePayload.put("newAssignedUserId", request.getAssignedUserId().toString());
                detailsChanged = true;

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
                throw new BusinessException("LEAD_UNASSIGNED",
                        "Lead must be assigned to a sales rep before its status can change.",
                        "assignedUserId",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateStatusTransition(previousStatus, newStatus);
            lead.setStatus(newStatus);
            if (newStatus == LeadStatus.CONVERTED && lead.getConvertedAt() == null) {
                lead.setConvertedAt(OffsetDateTime.now());
            }

            if (newStatus != previousStatus) {
                try {
                    ObjectNode payload = objectMapper.createObjectNode()
                            .put("previousStatus", previousStatus.name())
                            .put("newStatus", newStatus.name());
                    activityLogPublisher.publish(
                            ActivityLogType.LEAD_STATUS_UPDATED,
                            EntityType.LEAD,
                            lead.getLeadId(),
                            "Lead status updated from " + previousStatus + " to " + newStatus,
                            payload
                    );
                } catch (Exception e) {
                    log.warn("Failed to publish lead status update activity: {}", e.getMessage());
                }
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

        // BR-05: validate active follow-up details are present on the resulting state
        assertQualifyingDetailsPresent(lead);

        // BR: an organization lead must name its company. Validate the resulting state,
        // since either isCorporate or companyName may have just changed.
        if (Boolean.TRUE.equals(lead.getIsCorporate()) && !StringUtils.hasText(lead.getCompanyName())) {
            throw new IllegalArgumentException("Company name is required for an organization lead.");
        }

        if (detailsChanged) {
            try {
                activityLogPublisher.publish(
                        ActivityLogType.LEAD_UPDATED,
                        EntityType.LEAD,
                        lead.getLeadId(),
                        "Lead details updated",
                        updatePayload
                );
            } catch (Exception e) {
                log.warn("Failed to publish lead update activity: {}", e.getMessage());
            }
        }

        return LeadResponse.from(leadRepository.save(lead));
    }

    /**
     * BR-05 — a lead in active follow-up must carry the details that make follow-up possible.
     *
     * <p><b>Checked on the resulting record, not on the transition.</b> It used to run only when a
     * lead left {@code NEW}, which guarded the doorway and then stopped looking: a lead already in
     * {@code CONTACTED} could have its email blanked in a later edit — {@code @Email} accepts an
     * empty string — and stay in active follow-up with no way to reach it. Validating the state
     * about to be saved closes that, and covers every future edit path for free.
     *
     * <p>Placed beside the corporate-company check above, which was already written this way.
     *
     * <p>{@code NEW} and {@code LOST} are exempt by design: a walk-in must be recordable in
     * seconds, and a junk lead must be closable immediately rather than after filling in details
     * nobody will use.
     *
     * <p>Reports <em>all</em> missing fields at once, in {@code details}, so the form can mark them
     * together instead of surfacing them one refused save at a time.
     */
    private void assertQualifyingDetailsPresent(LeadEntity lead) {
        if (lead.getStatus() != LeadStatus.CONTACTED && lead.getStatus() != LeadStatus.QUALIFIED) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(lead.getEmail()) && !StringUtils.hasText(lead.getPhone())) {
            missing.add("phoneOrEmail");
        }
        if (!StringUtils.hasText(lead.getSource())) {
            missing.add("source");
        }
        if (!StringUtils.hasText(lead.getInterestedService())) {
            missing.add("interestedService");
        }
        if (missing.isEmpty()) {
            return;
        }
        throw new BusinessException(
                "LEAD_NOT_READY_FOR_FOLLOW_UP",
                "A lead in active follow-up needs " + describe(missing)
                        + ". Fill them in, then change the status.",
                String.join(",", missing),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Turns the machine-readable field list into the sentence a user actually reads. */
    private static String describe(List<String> missing) {
        List<String> labels = missing.stream()
                .map(f -> switch (f) {
                    case "phoneOrEmail" -> "a phone number or email";
                    case "source" -> "an inquiry source";
                    case "interestedService" -> "an interested service";
                    default -> f;
                })
                .toList();
        return labels.size() == 1
                ? labels.get(0)
                : String.join(", ", labels.subList(0, labels.size() - 1))
                        + " and " + labels.get(labels.size() - 1);
    }

    /**
     * Rejects an edit that would move this lead's email or phone onto one another lead already has.
     *
     * <p>Mirrors {@code CreateLeadUseCase.assertNotDuplicate} — same repository lookups, same
     * exception carrying the existing lead's id so the UI can link to it — with one addition: a
     * match on the lead being edited is ignored. Re-saving a form without touching the contact
     * fields must not report the record as a duplicate of itself.
     *
     * <p>A blank value clears the field rather than claiming an identity, so it is skipped.
     */
    private void assertNotDuplicate(UpdateLeadRequest request, LeadEntity lead) {
        UUID selfId = lead.getLeadId();
        if (StringUtils.hasText(request.getEmail())) {
            String email = request.getEmail().trim();
            leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                    .filter(other -> !selfId.equals(other.getLeadId()))
                    .ifPresent(other -> {
                        throw new DuplicateLeadException("email", email, other.getLeadId());
                    });
        }
        if (StringUtils.hasText(request.getPhone())) {
            String phone = request.getPhone().trim();
            leadRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                    .filter(other -> !selfId.equals(other.getLeadId()))
                    .ifPresent(other -> {
                        throw new DuplicateLeadException("phone number", phone, other.getLeadId());
                    });
        }
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
            throw invalidTransition("A converted lead's status is locked and cannot be changed.");
        }
        if (current == LeadStatus.LOST) {
            throw invalidTransition("A lost lead's status can no longer be changed.");
        }
        if (next == LeadStatus.CONVERTED) {
            throw invalidTransition(
                    "A lead can only become CONVERTED through the conversion flow, not a status update.");
        }
        if (next == LeadStatus.LOST) {
            return; // an active lead can always be marked Lost
        }
        if (FORWARD.get(current) != next) {
            throw invalidTransition("Invalid status transition: " + current + " → " + next
                    + ". Leads move forward one stage at a time (NEW → CONTACTED → QUALIFIED).");
        }
    }

    /** All transition refusals share one code, so the UI can point them at the status field. */
    private static BusinessException invalidTransition(String message) {
        return new BusinessException("LEAD_INVALID_TRANSITION", message, "status",
                HttpStatus.UNPROCESSABLE_ENTITY);
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
