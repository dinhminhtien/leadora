package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InteractionTimelineSpecification {

    private InteractionTimelineSpecification() {
    }

    public static Specification<InteractTimelineEntity> filter(
            String search,
            String type,
            UUID agentId,
            boolean unscoped,
            UUID scopedUserId
    ) {
        return (root, query, cb) -> {
            // Eager load associations to avoid N+1 queries
            if (Long.class != query.getResultType()) {
                root.fetch("user",     JoinType.LEFT);
                root.fetch("lead",     JoinType.LEFT);
                root.fetch("customer", JoinType.LEFT);
                root.fetch("deal",     JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // Search by description, agent name, lead name, customer name, deal name
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                var userJoin = root.join("user", JoinType.LEFT);
                var leadJoin = root.join("lead", JoinType.LEFT);
                var customerJoin = root.join("customer", JoinType.LEFT);
                var dealJoin = root.join("deal", JoinType.LEFT);

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(userJoin.get("fullName")), pattern),
                        cb.like(cb.lower(leadJoin.get("fullName")), pattern),
                        cb.like(cb.lower(customerJoin.get("fullName")), pattern),
                        cb.like(cb.lower(dealJoin.get("dealName")), pattern)
                ));
            }

            // Filter by interactionType
            if (type != null && !type.isBlank() && !"all".equalsIgnoreCase(type)) {
                predicates.add(cb.equal(cb.lower(root.get("interactionType")), type.toLowerCase().trim()));
            }

            // Filter by agentId (request param)
            if (agentId != null) {
                var userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.equal(userJoin.get("userId"), agentId));
            }

            // Security visibility scoping for Sales Staff
            if (!unscoped && scopedUserId != null) {
                var userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.equal(userJoin.get("userId"), scopedUserId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
