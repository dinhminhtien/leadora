package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ReminderSpecification {

    private ReminderSpecification() {
    }

    public static Specification<ReminderEntity> filter(
            UUID userId,
            ReminderStatus statusFilter,
            boolean excludeCancelled,
            OffsetDateTime remindFrom,
            OffsetDateTime remindTo,
            String sortBy
    ) {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("createdBy", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("userId"), userId));
            }

            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            } else if (excludeCancelled) {
                predicates.add(cb.notEqual(root.get("status"), ReminderStatus.CANCELLED));
            }

            if (remindFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("remindAt"), remindFrom));
            }

            if (remindTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("remindAt"), remindTo));
            }

            if (Long.class != query.getResultType()) {
                if ("priority".equalsIgnoreCase(sortBy)) {
                    query.orderBy(
                            cb.asc(
                                    cb.selectCase()
                                            .when(cb.equal(root.get("priority"), ReminderPriority.HIGH), 0)
                                            .when(cb.equal(root.get("priority"), ReminderPriority.MEDIUM), 1)
                                            .when(cb.equal(root.get("priority"), ReminderPriority.LOW), 2)
                                            .otherwise(3)
                            ),
                            cb.asc(root.get("remindAt"))
                    );
                } else {
                    query.orderBy(cb.asc(root.get("remindAt")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
