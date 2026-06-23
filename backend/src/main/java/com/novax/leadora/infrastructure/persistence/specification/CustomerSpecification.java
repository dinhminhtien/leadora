package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Composable predicates for {@link CustomerEntity} dynamic filtering.
 *
 * <p>Usage example:
 * <pre>{@code
 *   Specification<CustomerEntity> spec = Specification.allOf(
 *       CustomerSpecification.search(keyword),
 *       CustomerSpecification.hasStatus(status),
 *       CustomerSpecification.hasType(type)
 *   );
 *   repository.findAll(spec, PageRequest.of(page, size, Sort.by(direction, sortField)));
 * }</pre>
 *
 * <h3>Recommended database indexes</h3>
 * <pre>
 *   -- Equality filters
 *   CREATE INDEX idx_customers_status     ON customers(status);
 *   CREATE INDEX idx_customers_type       ON customers(customer_type);
 *   CREATE INDEX idx_customers_assigned   ON customers(assigned_user_id);
 *
 *   -- Default sort
 *   CREATE INDEX idx_customers_created_at ON customers(created_at DESC);
 *
 *   -- For PostgreSQL: trigram indexes for efficient LIKE searches
 *   CREATE EXTENSION IF NOT EXISTS pg_trgm;
 *   CREATE INDEX idx_customers_fullname_trgm  ON customers USING GIN (lower(full_name)    gin_trgm_ops);
 *   CREATE INDEX idx_customers_email_trgm     ON customers USING GIN (lower(email)        gin_trgm_ops);
 *   CREATE INDEX idx_customers_phone_trgm     ON customers USING GIN (lower(phone)        gin_trgm_ops);
 *   CREATE INDEX idx_customers_company_trgm   ON customers USING GIN (lower(company_name) gin_trgm_ops);
 * </pre>
 */
public final class CustomerSpecification {

    private CustomerSpecification() {}

    /** Case-insensitive LIKE match on fullName, email, phone, and companyName. */
    public static Specification<CustomerEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("phone")), pattern),
                cb.like(cb.lower(root.get("companyName")), pattern)
        );
    }

    public static Specification<CustomerEntity> hasStatus(CustomerStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<CustomerEntity> hasType(CustomerType type) {
        if (type == null) return null;
        return (root, query, cb) -> cb.equal(root.get("customerType"), type);
    }
}
