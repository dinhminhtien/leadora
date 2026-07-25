package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.NotificationPriority;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dynamic filter predicates for UC-15.1 (type / priority / date-range / read state),
 * combined via {@link JpaSpecificationExecutor} instead of one derived-query method per
 * filter combination.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<NotificationEntity> userId(UUID userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("userId"), userId);
    }

    public static Specification<NotificationEntity> unreadOnly(boolean unread) {
        return (root, query, cb) -> unread ? cb.isFalse(root.get("isRead")) : null;
    }

    public static Specification<NotificationEntity> type(String type) {
        return (root, query, cb) -> (type == null || type.isBlank()) ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<NotificationEntity> priority(String priority) {
        return (root, query, cb) -> {
            if (priority == null || priority.isBlank()) {
                return null;
            }
            try {
                return cb.equal(root.get("priority"), NotificationPriority.valueOf(priority.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return cb.disjunction(); // unknown priority value → no matches, not a 500
            }
        };
    }

    public static Specification<NotificationEntity> createdFrom(OffsetDateTime from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<NotificationEntity> createdTo(OffsetDateTime to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    /**
     * Orders URGENT → HIGH → NORMAL → LOW (ties broken by newest first) instead of the
     * alphabetical order a plain {@code ORDER BY priority} would give (HIGH, LOW, NORMAL, URGENT).
     * Sets the order directly on the query — a Specification composed via {@code .and(...)}
     * may return a null predicate purely for this side effect.
     */
    public static Specification<NotificationEntity> orderByPrioritySeverityThenCreatedAtDesc() {
        return (root, query, cb) -> {
            Expression<Integer> severity = cb.<Integer>selectCase()
                    .when(cb.equal(root.get("priority"), NotificationPriority.URGENT), 4)
                    .when(cb.equal(root.get("priority"), NotificationPriority.HIGH), 3)
                    .when(cb.equal(root.get("priority"), NotificationPriority.NORMAL), 2)
                    .otherwise(1);
            query.orderBy(cb.desc(severity), cb.desc(root.get("createdAt")));
            return null;
        };
    }
}
