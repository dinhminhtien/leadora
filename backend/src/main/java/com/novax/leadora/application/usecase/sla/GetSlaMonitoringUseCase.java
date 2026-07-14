package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.response.SlaMonitoringResponse;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSlaMonitoringUseCase {

    private final SlaTrackingRepository slaTrackingRepository;

    private static final List<SlaStatus> ACTIVE_STATUSES = List.of(SlaStatus.ACTIVE, SlaStatus.BREACHED);

    /**
     * @param entityType    optional filter — LEAD | QUOTATION | BOOKING | TASK
     * @param displayStatus optional filter — WITHIN_SLA | WARNING | BREACHED
     */
    @Transactional(readOnly = true)
    public List<SlaMonitoringResponse> execute(String entityType, String displayStatus) {
        OffsetDateTime now = OffsetDateTime.now();

        // Push status + entityType filter to DB — avoid loading RESOLVED records
        List<SlaTrackingEntity> records = StringUtils.hasText(entityType)
                ? slaTrackingRepository.findByStatusInAndEntityType(ACTIVE_STATUSES, entityType.toUpperCase())
                : slaTrackingRepository.findByStatusIn(ACTIVE_STATUSES);

        return records.stream()
                // E3: skip records with missing data
                .filter(t -> t.getEntityId() != null && t.getDeadlineAt() != null)
                .map(t -> SlaMonitoringResponse.from(t, now))
                .filter(r -> !StringUtils.hasText(displayStatus)
                        || r.getDisplayStatus().equalsIgnoreCase(displayStatus))
                // Most urgent first: BREACHED → WARNING → WITHIN_SLA, then by deadlineAt ASC
                .sorted(Comparator
                        .comparingInt((SlaMonitoringResponse r) -> displayStatusOrder(r.getDisplayStatus()))
                        .thenComparing(r -> r.getDeadlineAt()))
                .toList();
    }

    private int displayStatusOrder(String status) {
        return switch (status) {
            case "BREACHED"   -> 0;
            case "WARNING"    -> 1;
            case "WITHIN_SLA" -> 2;
            default           -> 3;
        };
    }
}
