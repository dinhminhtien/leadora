package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.response.SlaMonitoringResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSlaMonitoringUseCase {

    private static final Set<String> SCOPED_ROLES = Set.of("SALES", "SALES_STAFF");

    private final SlaTrackingRepository slaTrackingRepository;
    private final CurrentUserProvider currentUserProvider;
    private final LeadRepository leadRepository;
    private final QuotationRepository quotationRepository;
    private final TaskRepository taskRepository;

    private static final List<SlaStatus> ACTIVE_STATUSES = List.of(SlaStatus.ACTIVE, SlaStatus.BREACHED);

    /**
     * @param entityType    optional filter — LEAD | QUOTATION | TASK | PAYMENT | HANDOVER
     * @param displayStatus optional filter — WITHIN_SLA | WARNING | BREACHED
     */
    @Transactional(readOnly = true)
    public List<SlaMonitoringResponse> execute(String entityType, String displayStatus) {
        OffsetDateTime now = OffsetDateTime.now();

        // Push status + entityType filter to DB — avoid loading RESOLVED records
        List<SlaTrackingEntity> records = StringUtils.hasText(entityType)
                ? slaTrackingRepository.findByStatusInAndEntityType(ACTIVE_STATUSES, entityType.toUpperCase())
                : slaTrackingRepository.findByStatusIn(ACTIVE_STATUSES);

        // A Sales Staff only sees SLA activity on their own leads/quotations/tasks —
        // not the whole org's board. Manager/Admin (and any other role) see everything.
        UserEntity currentUser = currentUserProvider.resolve(null);
        String role = currentUser.getRole() != null && currentUser.getRole().getRoleName() != null
                ? currentUser.getRole().getRoleName().trim().toUpperCase() : "";
        if (SCOPED_ROLES.contains(role)) {
            UUID uid = currentUser.getUserId();
            records = records.stream()
                    .filter(t -> uid.equals(resolveOwnerId(t)))
                    .toList();
        }

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

    /** Resolves the sales rep who owns a tracking record's underlying entity, or null. */
    private UUID resolveOwnerId(SlaTrackingEntity tracking) {
        if (tracking.getEntityId() == null) return null;
        try {
            return switch (tracking.getEntityType()) {
                case "LEAD" -> leadRepository.findById(tracking.getEntityId())
                        .map(l -> l.getAssignedUser() != null ? l.getAssignedUser().getUserId() : null)
                        .orElse(null);
                case "QUOTATION" -> quotationRepository.findById(tracking.getEntityId())
                        .map(q -> q.getCreatedBy() != null ? q.getCreatedBy().getUserId() : null)
                        .orElse(null);
                case "TASK" -> taskRepository.findById(tracking.getEntityId())
                        .map(t -> t.getAssignedUser() != null ? t.getAssignedUser().getUserId() : null)
                        .orElse(null);
                // PAYMENT/HANDOVER are Reservation/Front Office concerns, not a sales rep's own
                // work — excluded from the scoped (Sales) view rather than guessed at.
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
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
