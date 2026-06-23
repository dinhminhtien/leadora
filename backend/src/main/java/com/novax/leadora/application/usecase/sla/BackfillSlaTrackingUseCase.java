package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaRuleRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillSlaTrackingUseCase {

    private final QuotationRepository quotationRepository;
    private final SlaTrackingRepository slaTrackingRepository;
    private final SlaRuleRepository slaRuleRepository;

    private static final Set<QuotationStatus> COMPLETED_STATUSES = Set.of(
            QuotationStatus.SENT, QuotationStatus.ACCEPTED, QuotationStatus.INTERESTED,
            QuotationStatus.CONVERTED, QuotationStatus.CLOSED, QuotationStatus.EXPIRED,
            QuotationStatus.REJECTED
    );

    /**
     * Creates SLA tracking records for existing quotations that were created before
     * a QUOTATION_SENT rule existed. Safe to call multiple times — skips already-tracked entities.
     *
     * @return number of new tracking records created
     */
    @Transactional
    public int executeForQuotations() {
        var ruleOpt = slaRuleRepository.findByActivityTypeAndActiveTrue("QUOTATION_SENT");
        if (ruleOpt.isEmpty()) {
            log.warn("Backfill skipped — no active QUOTATION_SENT rule configured");
            return 0;
        }
        var rule = ruleOpt.get();

        Set<UUID> alreadyTracked = slaTrackingRepository.findByEntityType("QUOTATION")
                .stream()
                .map(SlaTrackingEntity::getEntityId)
                .collect(Collectors.toSet());

        List<QuotationEntity> untracked = quotationRepository.findAll()
                .stream()
                .filter(q -> !alreadyTracked.contains(q.getQuotationId()))
                .toList();

        if (untracked.isEmpty()) {
            log.info("Backfill: all quotations already have SLA tracking");
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int count = 0;

        for (QuotationEntity q : untracked) {
            OffsetDateTime startTime = q.getCreatedAt() != null ? q.getCreatedAt() : now;
            OffsetDateTime deadlineAt = startTime.plusHours(rule.getDeadlineHours());
            boolean isCompleted = COMPLETED_STATUSES.contains(q.getStatus());

            OffsetDateTime resolvedAt = null;
            SlaStatus status = SlaStatus.ACTIVE;

            if (isCompleted) {
                status = SlaStatus.RESOLVED;
                // Use sentAt as resolve time if available; fall back to startTime
                resolvedAt = q.getSentAt() != null ? q.getSentAt() : startTime;
            }

            SlaTrackingEntity tracking = SlaTrackingEntity.builder()
                    .ruleId(rule.getRuleId())
                    .entityType("QUOTATION")
                    .entityId(q.getQuotationId())
                    .activityType("QUOTATION_SENT")
                    .startedAt(startTime)
                    .deadlineAt(deadlineAt)
                    .warningAt(deadlineAt.minusHours(rule.getWarningThreshold()))
                    .escalationAt(deadlineAt.plusHours(rule.getEscalationThreshold()))
                    .status(status)
                    .resolvedAt(resolvedAt)
                    .build();

            slaTrackingRepository.save(tracking);
            count++;
        }

        log.info("SLA backfill completed: {} QUOTATION records created", count);
        return count;
    }
}
