package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Query helpers for the Front Office arrival-handover screens (UC-22.1). */
public final class OpHandoverSpecification {

    private OpHandoverSpecification() {}

    /**
     * Handovers visible to Front Office: everything that has been submitted (i.e. NOT a DRAFT
     * still owned by Sales/Reservation), optionally filtered by readiness, arrival date, and a
     * free-text search over booking code / guest name.
     */
    public static Specification<OpHandoverEntity> forFrontOffice(String search, ReadinessStatus readiness,
                                                                 LocalDate arrivalDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), HandoverStatus.DRAFT));

            if (readiness != null) {
                predicates.add(cb.equal(root.get("readinessStatus"), readiness));
            }

            if (StringUtils.hasText(search) || arrivalDate != null) {
                Join<?, ?> booking = root.join("booking", JoinType.LEFT);
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
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
