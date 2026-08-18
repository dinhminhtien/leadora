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

    /**
     * Ordering weight for the "Status" sort: higher value → shown first. Unknown → 0.
     *
     * <p><b>Ranked by how much attention a lead still needs, not by how far it has travelled.</b>
     * {@code CONVERTED} used to sit at the top, which put the one status with nothing left to do —
     * the record is finished and locked read-only by BR-08 — at the head of a working list. With
     * ten or more converted leads the first page contained no actionable work at all.
     *
     * <p>{@code QUALIFIED} leads the order because it is the state where delay costs the most: the
     * lead is ready to close and someone has to act. {@code CONVERTED} and {@code LOST} sink to the
     * bottom — both are closed, and missing one costs nothing.
     */
    private static final Map<LeadStatus, Integer> PRIORITY = Map.of(
            LeadStatus.QUALIFIED, 4,
            LeadStatus.CONTACTED, 3,
            LeadStatus.NEW,       2,
            LeadStatus.CONVERTED, 1,
            LeadStatus.LOST,      0
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
            boolean createdByMe,
            boolean unassignedOnly
    ) {
        return (root, query, cb) -> {

            // Fetch associations for data queries; skip for count queries.
            if (Long.class != query.getResultType()) {
                root.fetch("assignedUser", JoinType.LEFT);
                root.fetch("createdBy",    JoinType.LEFT);
                root.fetch("customer",     JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // Free-text: fullName, email, companyName, phone.
            //
            // Phone was missing, and it is the identifier a sales rep is most likely to have in
            // hand: the guest just called. A lead recorded with a phone and no email — which the
            // contact rule expressly allows — could not be found by any of the three fields
            // searched, so it was reachable only by scrolling.
            //
            // The number is matched on digits alone. Stored values are digits-only (the phone
            // pattern permits nothing else), while a person searching types the number the way
            // they read it — "0912 345 678" — and a literal LIKE would miss every time.
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                List<Predicate> textMatches = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("fullName")),    pattern),
                        cb.like(cb.lower(root.get("email")),       pattern),
                        cb.like(cb.lower(root.get("companyName")), pattern)
                ));

                String digits = phoneQuery(search);
                if (digits != null) {
                    textMatches.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }

                predicates.add(cb.or(textMatches.toArray(new Predicate[0])));
            }

            // "Assignment needed": leads nobody owns yet. These are invisible to a sales rep's
            // own list by definition — an unassigned lead is assigned to no one — so this is the
            // Manager's queue of work still to be distributed (BR-06: nothing starts until it is).
            if (unassignedOnly)                  predicates.add(cb.isNull(root.get("assignedUser")));

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

    /**
     * The digits to match a stored phone against, or {@code null} when this search term is not a
     * phone number at all.
     *
     * <p><b>Why not simply strip every non-digit.</b> That was the first version, and it made a
     * narrower search return more rows: {@code "minh 2"} reduced to {@code "2"}, which matches most
     * numbers on file, so it produced 23 hits where {@code "minh"} alone produced 12. Letters in
     * the term mean the user is looking for a person, not a number, and the phone clause must stay
     * out of it.
     *
     * <p>Separators <em>are</em> removed, because people read a number back the way they say it —
     * {@code "0912 345 678"} — while it is stored as one run of digits. The {@code +84} prefix is
     * folded onto the national {@code 0} for the same reason the clients fold it on input: one
     * number written two ways has to find the same lead, or search quietly depends on knowing which
     * form was typed when the record was created.
     */
    private static String phoneQuery(String search) {
        String compact = search.replaceAll("[\\s.()\\-]", "");
        if (compact.startsWith("+84")) {
            compact = "0" + compact.substring(3);
        }
        boolean isNumber = !compact.isEmpty() && compact.chars().allMatch(Character::isDigit);
        return isNumber ? compact : null;
    }
}
