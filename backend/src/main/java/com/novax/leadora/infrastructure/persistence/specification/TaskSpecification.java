package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class TaskSpecification {

    private TaskSpecification() {
    }

    /** Case-insensitive LIKE match on title and description. */
    public static Specification<TaskEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword))
            return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    public static Specification<TaskEntity> hasStatus(TaskStatus status) {
        if (status == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<TaskEntity> hasPriority(TaskPriority priority) {
        if (priority == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<TaskEntity> assignedTo(UUID userId) {
        if (userId == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("assignedUser").get("userId"), userId);
    }

    /**
     * Filters by customer. Tasks with no customer are excluded when this spec is
     * active.
     * When {@code customerId} is null the spec is omitted entirely, so customerless
     * tasks are included.
     */
    public static Specification<TaskEntity> forCustomer(UUID customerId) {
        if (customerId == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("customer").get("customerId"), customerId);
    }

    /**
     * Overdue = not COMPLETED, not CANCELLED, endAt non-null and already past.
     * OVERDUE is a computed state — it is never stored as a status value in the
     * database.
     */
    public static Specification<TaskEntity> isOverdue() {
        return (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), TaskStatus.COMPLETED),
                cb.notEqual(root.get("status"), TaskStatus.CANCELLED),
                root.get("endAt").isNotNull(),
                cb.lessThan(root.get("endAt"), OffsetDateTime.now()));
    }

    public static Specification<TaskEntity> defaultSort() {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType()))
                return cb.conjunction();
            query.orderBy(
                    cb.asc(cb.<Integer>selectCase()
                            .when(cb.equal(root.get("status"), TaskStatus.OPEN), 0)
                            .otherwise(1)),
                    cb.asc(cb.<Integer>selectCase()
                            .when(root.get("startAt").isNull(), 1)
                            .otherwise(0)),
                    cb.asc(root.get("startAt")),
                    cb.desc(root.get("updatedAt")));
            return cb.conjunction();
        };
    }
}
