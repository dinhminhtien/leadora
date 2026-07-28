package com.novax.leadora.application.usecase.activitylog;

import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetActivityLogUseCase {

    private final ActivityLogRepository activityLogRepository;

    @Data
    @Builder
    public static class FilterQuery {
        private String keyword;
        private ActorType actorType;
        private UUID actorUserId;
        private String actorRoleSnapshot;
        private ActivityLogType activityType;
        private EntityType entityType;
        private UUID entityId;
        private OffsetDateTime startDate;
        private OffsetDateTime endDate;
        private String view; // "RAW" or "EFFECTIVE"
        private String category; // "BUSINESS" or "SECURITY"
    }

    @Transactional(readOnly = true)
    public Page<ActivityLogEntity> execute(FilterQuery query, Pageable pageable) {
        Specification<ActivityLogEntity> spec = buildSpecification(query);
        return activityLogRepository.findAll(spec, pageable);
    }

    private Specification<ActivityLogEntity> buildSpecification(FilterQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Keyword search (summary or reason)
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                String match = "%" + query.getKeyword().toLowerCase() + "%";
                Predicate summaryMatch = cb.like(cb.lower(root.get("summary")), match);
                Predicate reasonMatch = cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), match);
                predicates.add(cb.or(summaryMatch, reasonMatch));
            }

            // 2. Exact Filters
            if (query.getActorType() != null) {
                predicates.add(cb.equal(root.get("actorType"), query.getActorType()));
            }
            if (query.getActorUserId() != null) {
                predicates.add(cb.equal(root.get("actorUser").get("userId"), query.getActorUserId()));
            }
            if (query.getActorRoleSnapshot() != null && !query.getActorRoleSnapshot().isBlank()) {
                predicates.add(cb.equal(root.get("actorRoleSnapshot"), query.getActorRoleSnapshot()));
            }
            if (query.getActivityType() != null) {
                predicates.add(cb.equal(root.get("activityType"), query.getActivityType()));
            }
            if (query.getEntityType() != null) {
                predicates.add(cb.equal(root.get("entityType"), query.getEntityType()));
            }

            if ("SECURITY".equalsIgnoreCase(query.getCategory())) {
                predicates.add(cb.equal(root.get("entityType"), EntityType.USER));
            } else if ("BUSINESS".equalsIgnoreCase(query.getCategory())) {
                predicates.add(cb.notEqual(root.get("entityType"), EntityType.USER));
            }
            if (query.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), query.getEntityId()));
            }

            // 3. Date range filters
            if (query.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.getStartDate()));
            }
            if (query.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.getEndDate()));
            }

            // 4. View Mode: RAW vs EFFECTIVE
            if ("EFFECTIVE".equalsIgnoreCase(query.getView())) {
                // Exclude VOIDED records
                Predicate notVoided = cb.notEqual(root.get("recordOperation"), RecordOperation.VOIDED);

                // Exclude records that have been referenced by any VOIDED or CORRECTED record
                Subquery<UUID> subquery = criteriaQuery.subquery(UUID.class);
                Root<ActivityLogEntity> subRoot = subquery.from(ActivityLogEntity.class);
                subquery.select(subRoot.get("refActivityId"));
                subquery.where(
                        cb.and(
                                cb.isNotNull(subRoot.get("refActivityId")),
                                subRoot.get("recordOperation").in(RecordOperation.VOIDED, RecordOperation.CORRECTED)
                        )
                );

                Predicate notReferencedAsModified = cb.not(root.get("id").in(subquery));
                predicates.add(cb.and(notVoided, notReferencedAsModified));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
