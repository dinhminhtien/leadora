package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartSlaTrackingUseCase {

    private final SlaRuleRepository slaRuleRepository;
    private final SlaTrackingRepository slaTrackingRepository;
    private final BusinessCalendarService businessCalendarService;

    /**
     * Starts SLA tracking for a newly created entity.
     * Runs in a separate transaction so a failure here never rolls back the parent operation.
     *
     * @param activityType SLA activity type key (e.g. "LEAD_RESPONSE")
     * @param entityType   Entity table name (e.g. "LEAD")
     * @param entityId     UUID of the created entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(String activityType, String entityType, UUID entityId) {
        // E4: defensive check on entity data
        if (entityId == null || activityType == null || entityType == null) {
            log.warn("SLA tracking skipped — null input: activityType={}, entityType={}, entityId={}",
                    activityType, entityType, entityId);
            return;
        }

        // E3: no active rule configured for this activity type
        var ruleOpt = slaRuleRepository.findByActivityTypeAndActiveTrue(activityType);
        if (ruleOpt.isEmpty()) {
            log.warn("SLA tracking skipped — no active rule for activityType={}", activityType);
            return;
        }

        var rule = ruleOpt.get();
        OffsetDateTime now = OffsetDateTime.now();
        // BR-32/BR-42: deadline is computed in business hours, not continuous wall-clock time.
        OffsetDateTime deadlineAt = businessCalendarService.addBusinessHours(now, rule.getDeadlineHours());

        SlaTrackingEntity tracking = SlaTrackingEntity.builder()
                .ruleId(rule.getRuleId())
                .entityType(entityType)
                .entityId(entityId)
                .activityType(activityType)
                .startedAt(now)
                .deadlineAt(deadlineAt)
                // warningAt fires BEFORE deadline
                .warningAt(businessCalendarService.subtractBusinessHours(deadlineAt, rule.getWarningThreshold()))
                // escalationAt fires AFTER deadline
                .escalationAt(businessCalendarService.addBusinessHours(deadlineAt, rule.getEscalationThreshold()))
                .status(SlaStatus.ACTIVE)
                .build();

        slaTrackingRepository.save(tracking);

        // POST-3: log SLA start for audit
        log.info("SLA tracking started: activityType={}, entityType={}, entityId={}, deadline={}",
                activityType, entityType, entityId, deadlineAt);
    }
}
