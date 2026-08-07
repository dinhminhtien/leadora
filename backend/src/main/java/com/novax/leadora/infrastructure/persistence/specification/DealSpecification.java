package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DealSpecification {

    private DealSpecification() {
    }

    /**
     * The deals a new quotation may be raised against (UC-14.1).
     *
     * <p><b>Eligibility is exactly one condition: the deal is still active.</b> A WON or
     * LOST deal is closed and immutable ({@code UpdateDealUseCase}: "Closed deals cannot be
     * modified."), so a fresh quotation against one has nowhere to go. Nothing else may
     * exclude an active deal.
     *
     * <p><b>Wire-format trap.</b> "ACTIVE" is a display value, not an enum constant:
     * {@code DealMapper.mapStatusToString} renders {@link DealStatus#OPEN} as {@code
     * "active"}, WON as {@code "won"} and LOST as {@code "lost"}. The persisted enum name
     * is {@code OPEN}, which is what this predicate must compare against — matching on the
     * string "ACTIVE" would silently return nothing.
     *
     * <p>An earlier version also required a linked customer, mirroring
     * {@code CreateQuotationUseCase}'s rejection of a deal with none. That predicate is
     * gone: {@code DealEntity.customer} is mapped {@code @JoinColumn(nullable = false)}, so
     * a deal without a customer cannot exist and the check could only ever have removed an
     * active deal it should not have. The use case keeps its own guard, which is the right
     * place for it — a picker filter is not where a not-null constraint gets enforced.
     *
     * <p>Owner scoping is inherited unchanged from {@link #filter}: a SALES user sees only
     * deals assigned to or created by them, MANAGER/ADMIN are unscoped.
     *
     * <p>No expected-close-date condition. A deal that slipped its close date is usually
     * the one most in need of a quotation; the date drives ordering instead.
     */
    public static Specification<DealEntity> quotable(
            String search,
            boolean unscoped,
            UUID scopedUserId
    ) {
        return filter(search, null, unscoped, scopedUserId)
                .and((root, query, cb) -> cb.equal(root.get("status"), DealStatus.OPEN));
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
