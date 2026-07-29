package com.novax.leadora.application.usecase.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.api.dto.request.ReopenLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * UC-8.4 — put a lead that was closed as LOST back into the pipeline.
 *
 * <p><b>Why this is its own use case rather than a status option.</b> Every other transition is a
 * field on {@code PUT /leads/{id}}, and {@code UpdateLeadUseCase} deliberately refuses to move a
 * lead out of {@code LOST}. Reopening is not the same kind of act: it undoes a recorded business
 * outcome — the figure the "Lost" tile counts and the conversion-rate denominator both change — so
 * it takes a Manager, a mandatory reason, and an audit entry, none of which an ordinary field
 * update carries. Folding it into the update path would have meant loosening the guard there for
 * every caller in order to serve one privileged case.
 *
 * <p><b>It reopens to {@code NEW}, not to whatever the lead was before.</b> A lead is marked lost
 * because contact stopped; when the guest comes back, the qualification that was true months ago is
 * not evidence about today, and restoring {@code QUALIFIED} would let it convert immediately on the
 * strength of it. {@code NEW} also sidesteps BR-05 by design — {@code assertQualifyingDetailsPresent}
 * exempts it — so a reopen never fails on a field the reopening manager was not shown.
 *
 * <p><b>What it deliberately does not do:</b> restart {@code LEAD_RESPONSE} SLA tracking. That row
 * was resolved when the lead left {@code NEW} originally, and {@code StartSlaTrackingUseCase} always
 * inserts rather than reusing — so a second ACTIVE row would sit beside the resolved one, and the
 * only reader of the pair ({@code SlaStatusBadge}, which takes the first match for the entity)
 * would show whichever the monitoring query happened to order first. Giving a reopened lead a fresh
 * response clock is worth doing; it needs the tracking lookup to pick a winner first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReopenLeadUseCase {

    private final LeadRepository leadRepository;
    private final LeadAccessPolicy leadAccessPolicy;
    private final AuditCorrectionService auditCorrectionService;
    private final SystemAuditLogService systemAuditLogService;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    private static final List<ActivityLogType> LEAD_FAMILY_TYPES = List.of(
            ActivityLogType.LEAD_CREATED,
            ActivityLogType.LEAD_STATUS_UPDATED,
            ActivityLogType.LEAD_CONVERTED,
            ActivityLogType.LEAD_UPDATED);

    @Transactional
    public LeadResponse execute(UUID leadId, ReopenLeadRequest request) {
        LeadEntity lead = leadRepository.findWithUsersByIdForUpdate(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        // Manager/Admin only — the same gate BR-07's conversion override uses. A rep reopening
        // their own lost leads would make the Lost figure something they can edit at will.
        UserEntity actor = leadAccessPolicy.currentUser();
        leadAccessPolicy.assertFullAccess(actor);

        // Ordered so the more specific refusal wins: a converted lead is told it is converted,
        // not that it "is not lost".
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new BusinessException("LEAD_LOCKED",
                    "This lead has been converted and is now a locked historical record; "
                            + "it cannot be reopened.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (lead.getStatus() != LeadStatus.LOST) {
            throw new BusinessException("LEAD_NOT_LOST",
                    "Only a lead closed as lost can be reopened. Current status: " + lead.getStatus(),
                    "status",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String reason = request.getReason().trim();
        lead.setStatus(LeadStatus.NEW);

        // Appended, not assigned — notes belong to the user. Same shape as the manager-approval
        // note LeadConversionCompleter writes, so the two read alike in the same field.
        String note = "[Reopened by " + (actor.getFullName() != null ? actor.getFullName() : "a manager")
                + ": " + reason + "]";
        lead.setNotes(StringUtils.hasText(lead.getNotes()) ? lead.getNotes() + "\n" + note : note);

        LeadEntity saved = leadRepository.save(lead);

        // Both trails are non-fatal: a reopen that succeeded must not be rolled back because a log
        // row failed to write. Same reasoning as LeadConversionCompleter.
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("previousStatus", LeadStatus.LOST.name())
                    .put("newStatus", LeadStatus.NEW.name())
                    .put("reason", reason);
            ActivityLogCommand command = ActivityLogCommand.builder()
                    .activityType(ActivityLogType.LEAD_STATUS_UPDATED)
                    .entityType(EntityType.LEAD)
                    .entityId(saved.getLeadId())
                    .summary("Lead reopened from LOST to NEW")
                    .payload(payload)
                    .reason(reason)
                    .build();
            auditCorrectionService.correctPriorActivity(saved.getLeadId(), LEAD_FAMILY_TYPES, command);
        } catch (Exception e) {
            log.warn("Failed to publish lead reopen activity for lead {}: {}", leadId, e.getMessage());
        }

        try {
            systemAuditLogService.log("LEAD", "LEAD", saved.getLeadId(), "REOPENED", actor,
                    LeadStatus.LOST.name(), LeadStatus.NEW.name(), reason);
        } catch (Exception e) {
            log.warn("Failed to write reopen audit entry for lead {}: {}", leadId, e.getMessage());
        }

        // The lead keeps its owner, so someone else's queue just grew by one without them asking.
        notifyOwner(saved, actor, reason);

        return LeadResponse.from(saved);
    }

    private void notifyOwner(LeadEntity lead, UserEntity actor, String reason) {
        UserEntity owner = lead.getAssignedUser();
        // A lead can be lost while still unassigned, and the manager reopening it is not told
        // anything they do not already know.
        if (owner == null || owner.getUserId().equals(actor.getUserId())) {
            return;
        }
        try {
            notificationRepository.save(NotificationEntity.builder()
                    .user(owner)
                    .title("Lead Reopened")
                    .message("The lead " + lead.getFullName() + " was reopened and is back in your pipeline: "
                            + reason)
                    .type("LEAD_REOPENED")
                    .relatedEntity("LEAD")
                    .relatedId(lead.getLeadId())
                    .build());
        } catch (Exception e) {
            log.warn("Lead-reopened notification failed for lead {}: {}", lead.getLeadId(), e.getMessage());
        }
    }
}
