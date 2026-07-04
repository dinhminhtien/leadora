package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeadSpecification {

    private LeadSpecification() {
    }

    /** Pipeline priority: higher value → shown first. LOST / unknown → 0. */
    private static final Map<LeadStatus, Integer> PRIORITY = Map.of(
            LeadStatus.CONVERTED, 4,
            LeadStatus.QUALIFIED, 3,
            LeadStatus.CONTACTED, 2,
            LeadStatus.NEW,       1
    );

    public static final Comparator<LeadEntity> STATUS_PRIORITY_COMPARATOR =
            Comparator.comparingInt((LeadEntity l) ->
                            PRIORITY.getOrDefault(l.getStatus(), 0))
                    .reversed()
                    .thenComparing(Comparator.comparing((LeadEntity l) -> l.getCreatedAt()).reversed());

    /**
     * Builds a single AND-combined {@link Specification} from the active filters.
     * Null / blank parameters are skipped — they contribute no predicate.
     */
    public static Specification<LeadEntity> filter(
            String search,
            LeadStatus status,
            String source,
            Boolean isCorporate,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            boolean unscoped,
            UUID ownerId,
            boolean createdByMe
    ) {
        return (root, query, cb) -> {

            // Fetch associations for data queries; skip for count queries.
            if (Long.class != query.getResultType()) {
                root.fetch("assignedUser", JoinType.LEFT);
                root.fetch("createdBy",    JoinType.LEFT);
                root.fetch("customer",     JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // Free-text: fullName, email, companyName
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")),    pattern),
                        cb.like(cb.lower(root.get("email")),       pattern),
                        cb.like(cb.lower(root.get("companyName")), pattern)
                ));
            }

            if (status     != null)              predicates.add(cb.equal(root.get("status"),      status));
            if (source     != null && !source.isBlank()) predicates.add(cb.equal(root.get("source"), source));
            if (isCorporate != null)             predicates.add(cb.equal(root.get("isCorporate"), isCorporate));
            if (dateFrom   != null)              predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            if (dateTo     != null)              predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"),   dateTo));

            // Owner scope: skip when unscoped (admin/manager). For a scoped caller
            // (SALES) the two views are mutually exclusive:
            //   createdByMe=false (default) → leads ASSIGNED to the caller
            //   createdByMe=true            → leads the caller CREATED
            // A self-created lead is unassigned by default, so it only appears under
            // the "Created by me" view — exactly as the staff workflow requires.
            if (!unscoped && ownerId != null) {
                if (createdByMe) {
                    var cbJoin = root.join("createdBy", JoinType.LEFT);
                    predicates.add(cb.equal(cbJoin.get("userId"), ownerId));
                } else {
                    var auJoin = root.join("assignedUser", JoinType.LEFT);
                    predicates.add(cb.equal(auJoin.get("userId"), ownerId));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
