package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class BookingSpecification {

    private BookingSpecification() {
    }

    public static Specification<BookingEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword))
            return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            // Join with customer
            Join<Object, Object> customerJoin = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("bookingCode")), pattern),
                    cb.like(cb.lower(customerJoin.get("fullName")), pattern)
            );
        };
    }

    public static Specification<BookingEntity> hasStatus(BookingStatus status) {
        if (status == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
