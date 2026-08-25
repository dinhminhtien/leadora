package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class BookingSpecification {

    private BookingSpecification() {
    }

    public static Specification<BookingEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword))
            return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("bookingCode")), pattern),
                cb.like(cb.lower(root.get("customer").get("fullName")), pattern));
    }

    public static Specification<BookingEntity> hasStatus(BookingStatus status) {
        if (status == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<BookingEntity> hasAssignedUser(java.util.UUID userId) {
        if (userId == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("assignedUser").get("userId"), userId);
    }
}
