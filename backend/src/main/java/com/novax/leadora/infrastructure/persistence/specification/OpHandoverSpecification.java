package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Query helpers for the Front Office arrival-handover screens (UC-22.1). */
public final class OpHandoverSpecification {

    private OpHandoverSpecification() {}

    /**
     * The booking states an arrival is still worth preparing for (UC-22.1 step 3: the list must
     * exclude handovers that have been "cancelled or removed").
     *
     * <p>Deliberately a whitelist rather than a "not cancelled" blacklist: a {@link BookingStatus}
     * added later then defaults to <em>hidden</em> from the front desk instead of silently
     * appearing on it. CANCELLED / REJECTED / NO_SHOW are gone, and CHECKED_OUT is finished — the
     * guest has left, so keeping it on the arrival desk only grows the list without bound.
     */
    private static final Set<BookingStatus> ARRIVAL_RELEVANT_BOOKING_STATUSES =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    /**
     * Handovers visible to Front Office: everything that has been submitted (i.e. NOT a DRAFT
     * still owned by Sales/Reservation) <em>and</em> whose booking is still live, optionally
     * filtered by readiness, arrival date, assignee, and a free-text search over booking code /
     * guest name.
     *
     * @param assignedFoUserId explicit assignee filter (UC-22.1 step 5). Honoured only for
     *                         supervisors — see {@code ArrivalDeskScope.canFilterByAssignee}.
     * @param scopedToFoUserId server-derived scope (UC-22.1 step 3): when non-null the result is
     *                         narrowed to arrivals assigned to this user <em>or not yet assigned
     *                         to anybody</em>. Never taken from a request parameter, so a caller
     *                         cannot widen its own scope. {@code null} = every arrival.
     */
    public static Specification<OpHandoverEntity> forFrontOffice(String search, ReadinessStatus readiness,
                                                                 LocalDate arrivalDate,
                                                                 UUID assignedFoUserId,
                                                                 UUID scopedToFoUserId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), HandoverStatus.DRAFT));

            if (readiness != null) {
                predicates.add(cb.equal(root.get("readinessStatus"), readiness));
            }

            // `assigned_fo_user_id` is a scalar UUID column, not a relation, so both of these are
            // plain column comparisons — no join, no risk of an implicit inner join dropping rows.
            if (assignedFoUserId != null) {
                predicates.add(cb.equal(root.get("assignedFoUserId"), assignedFoUserId));
            }
            if (scopedToFoUserId != null) {
                // "or not yet assigned": an unassigned arrival must stay visible to the desk
                // instead of belonging to nobody. See ArrivalDeskScope for why.
                predicates.add(cb.or(
                        cb.equal(root.get("assignedFoUserId"), scopedToFoUserId),
                        cb.isNull(root.get("assignedFoUserId"))
                ));
            }

            // Always joined now: the booking's own status decides whether the arrival is still
            // real. `booking_id` is NOT NULL, so an inner join cannot drop a legitimate row.
            Join<?, ?> booking = root.join("booking", JoinType.INNER);
            predicates.add(booking.get("status").in(ARRIVAL_RELEVANT_BOOKING_STATUSES));

            if (arrivalDate != null) {
                predicates.add(cb.equal(booking.get("checkInDate"), arrivalDate));
            }
            if (StringUtils.hasText(search)) {
                Join<?, ?> customer = booking.join("customer", JoinType.LEFT);
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(booking.get("bookingCode")), like),
                        cb.like(cb.lower(customer.get("fullName")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Handovers visible to Sales / Reservation, optionally filtered by status, arrival date and
     * search keyword.
     *
     * @param ownerId when non-null, restricts the result to handovers this user owns — they wrote
     *                it, or the booking behind it is assigned to them (BR-01 / BR-02). Pass
     *                {@code null} for the unscoped Manager/Admin view. Resolved by
     *                {@code HandoverAccessPolicy.listScopeOwnerId}, never by the caller.
     */
    public static Specification<OpHandoverEntity> forOperations(String search, HandoverStatus status,
                                                                LocalDate arrivalDate, UUID ownerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // The booking join is needed by the ownership check as well as the filters, so it is
            // resolved once and reused. LEFT, because ownership may come from created_by alone.
            Join<?, ?> booking = root.join("booking", JoinType.LEFT);

            if (ownerId != null) {
                // Explicit LEFT joins on purpose: `root.get("createdBy").get("userId")` can be
                // rendered as an implicit INNER join, which would silently drop a handover whose
                // created_by is null even when its booking is assigned to this very user — the
                // OR would then never get the chance to match.
                Join<?, ?> createdBy = root.join("createdBy", JoinType.LEFT);
                Join<?, ?> assignedUser = booking.join("assignedUser", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.equal(createdBy.get("userId"), ownerId),
                        cb.equal(assignedUser.get("userId"), ownerId)
                ));
            }

            if (arrivalDate != null) {
                predicates.add(cb.equal(booking.get("checkInDate"), arrivalDate));
            }
            if (StringUtils.hasText(search)) {
                Join<?, ?> customer = booking.join("customer", JoinType.LEFT);
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(booking.get("bookingCode")), like),
                        cb.like(cb.lower(customer.get("fullName")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
