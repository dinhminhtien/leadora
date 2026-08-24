package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class PaymentSpecification {

    private PaymentSpecification() {}

    public static Specification<PaymentEntity> filterPayments(String search, PaymentStatus status, PaymentType paymentType, java.util.UUID ownerId) {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (paymentType != null) {
                predicates.add(cb.equal(root.get("paymentType"), paymentType));
            }

            Join<?, ?> booking = root.join("booking", JoinType.LEFT);
            Join<?, ?> customer = booking.join("customer", JoinType.LEFT);

            if (ownerId != null) {
                Join<?, ?> createdBy = root.join("createdBy", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.equal(booking.get("assignedUser").get("userId"), ownerId),
                        cb.equal(createdBy.get("userId"), ownerId),
                        cb.equal(customer.get("assignedUser").get("userId"), ownerId)
                ));
            }

            if (StringUtils.hasText(search)) {
                String like = "%" + search.trim().toLowerCase() + "%";
                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(booking.get("bookingCode")), like));
                searchPredicates.add(cb.like(cb.lower(customer.get("fullName")), like));
                
                // Also search email, phone, gatewayTransactionId, paymentMethod, verificationNote
                searchPredicates.add(cb.like(cb.lower(cb.coalesce(customer.get("email"), "")), like));
                searchPredicates.add(cb.like(cb.lower(cb.coalesce(customer.get("phone"), "")), like));
                searchPredicates.add(cb.like(cb.lower(cb.coalesce(root.get("gatewayTransactionId"), "")), like));
                searchPredicates.add(cb.like(cb.lower(cb.coalesce(root.get("paymentMethod"), "")), like));
                searchPredicates.add(cb.like(cb.lower(cb.coalesce(root.get("verificationNote"), "")), like));

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
