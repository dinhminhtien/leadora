package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class CustomerSpecification {

    private CustomerSpecification() {
    }

    public static Specification<CustomerEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword))
            return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("phone")), pattern),
                cb.like(cb.lower(root.get("companyName")), pattern));
    }

    public static Specification<CustomerEntity> hasStatus(CustomerStatus status) {
        if (status == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<CustomerEntity> hasType(CustomerType type) {
        if (type == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("customerType"), type);
    }
}
