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

    public static Specification<PaymentEntity> filterPayments(String search, PaymentStatus status, PaymentType paymentType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (paymentType != null) {
                predicates.add(cb.equal(root.get("paymentType"), paymentType));
            }

            if (StringUtils.hasText(search)) {
                Join<?, ?> booking = root.join("booking", JoinType.LEFT);
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
