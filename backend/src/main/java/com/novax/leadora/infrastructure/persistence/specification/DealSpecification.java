package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DealSpecification {

    private DealSpecification() {
    }

    public static Specification<DealEntity> filter(
            String search,
            UUID ownerId,
            boolean unscoped,
            UUID scopedUserId
    ) {
        return (root, query, cb) -> {
            // Eager load associations to avoid N+1 queries
            if (Long.class != query.getResultType()) {
                root.fetch("assignedUser", JoinType.LEFT);
                root.fetch("createdBy",    JoinType.LEFT);
                root.fetch("customer",     JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // Search by dealName, customer fullName, customer companyName
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                var customerJoin = root.join("customer", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("dealName")), pattern),
                        cb.like(cb.lower(customerJoin.get("fullName")), pattern),
                        cb.like(cb.lower(customerJoin.get("companyName")), pattern)
                ));
            }

            // Owner-based filter (from request parameters)
            if (ownerId != null) {
                var assignedJoin = root.join("assignedUser", JoinType.LEFT);
                predicates.add(cb.equal(assignedJoin.get("userId"), ownerId));
            }

            // Security visibility scoping for Sales Staff
            if (!unscoped && scopedUserId != null) {
                var assignedJoin = root.join("assignedUser", JoinType.LEFT);
                var createdJoin = root.join("createdBy", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.equal(assignedJoin.get("userId"), scopedUserId),
                        cb.equal(createdJoin.get("userId"), scopedUserId)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
