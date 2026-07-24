package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveSlaBreachUseCase {

    private static final Set<String> FULL_ACCESS_ROLES = Set.of("MANAGER", "ADMIN");

    private final SlaTrackingRepository slaTrackingRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SlaEntityResolver slaEntityResolver;
    private final SystemAuditLogService systemAuditLogService;

    /**
     * UC-17.4 step 5-6: User resolves a breached SLA — marks tracking record as RESOLVED.
     *
     * @param trackingId UUID of the SlaTracking record to resolve
     */
    @Transactional
    public void execute(UUID trackingId) {
        SlaTrackingEntity tracking = slaTrackingRepository.findById(trackingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "SLA tracking record not found"));

        // E3: activity already completed
        if (tracking.getStatus() == SlaStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Activity already completed");
        }

        // BR-02: only the assignee of the underlying entity or a manager/admin may resolve it
        UserEntity actor = currentUserProvider.resolve(null);
        String role = actor.getRole() != null ? actor.getRole().getRoleName() : "";
        boolean isFullAccess = FULL_ACCESS_ROLES.contains(role);
        UserEntity assigned = isFullAccess ? null : slaEntityResolver.resolveAssignedUser(tracking);
        boolean isAssignee = assigned != null && assigned.getUserId().equals(actor.getUserId());
        if (!isFullAccess && !isAssignee) {
            throw new AccessDeniedException("You do not have permission to resolve this SLA breach.");
        }

        // POST-2: update SLA status
        tracking.setStatus(SlaStatus.RESOLVED);
        tracking.setResolvedAt(java.time.OffsetDateTime.now());
        slaTrackingRepository.save(tracking);

        // POST-3: audit log
        systemAuditLogService.log("SLA", "SLA_TRACKING", trackingId, "RESOLVED", actor,
                tracking.getStatus().name(), SlaStatus.RESOLVED.name(),
                "entityType=" + tracking.getEntityType() + ", entityId=" + tracking.getEntityId());
        log.info("SLA breach resolved: trackingId={}, entityType={}, entityId={}",
                trackingId, tracking.getEntityType(), tracking.getEntityId());
    }

    /**
     * Auto-resolve all active SLA tracking records for an entity when the monitored
     * action completes (e.g. quotation sent, task resolved, booking confirmed).
     * Non-throwing — caller should wrap in try/catch to keep parent flow non-fatal.
     *
     * @param entityType e.g. "QUOTATION", "TASK", "LEAD"
     * @param entityId   UUID of the entity that completed its action
     */
    @Transactional
    public void executeByEntity(String entityType, UUID entityId) {
        List<SlaTrackingEntity> records =
                slaTrackingRepository.findByEntityTypeAndEntityId(entityType, entityId);
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        records.stream()
                .filter(t -> t.getStatus() != SlaStatus.RESOLVED)
                .forEach(t -> {
                    t.setStatus(SlaStatus.RESOLVED);
                    t.setResolvedAt(now);
                    slaTrackingRepository.save(t);
                    log.info("SLA auto-resolved: entityType={}, entityId={}, trackingId={}",
                            entityType, entityId, t.getTrackingId());
                });
    }
}