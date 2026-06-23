package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Composable predicates for {@link TaskEntity} dynamic filtering.
 *
 * <p>Every method returns {@code null} when the filter value is absent.
 * {@code null} specs are treated as a no-op (conjunction) by {@link Specification#allOf},
 * so callers can pass them unconditionally without null-checks.
 *
 * <p>Usage example:
 * <pre>{@code
 *   Specification<TaskEntity> spec = Specification.allOf(
 *       TaskSpecification.search(keyword),
 *       TaskSpecification.hasStatus(status),
 *       TaskSpecification.hasPriority(priority),
 *       TaskSpecification.assignedTo(userId),
 *       TaskSpecification.forCustomer(customerId),
 *       overdue ? TaskSpecification.isOverdue() : null,
 *       TaskSpecification.defaultSort()
 *   );
 *   repository.findAll(spec, PageRequest.of(page, size));
 * }</pre>
 *
 * <h3>Recommended database indexes</h3>
 * <pre>
 *   -- Equality filters
 *   CREATE INDEX idx_tasks_status    ON tasks(status);
 *   CREATE INDEX idx_tasks_priority  ON tasks(priority);
 *   CREATE INDEX idx_tasks_assigned  ON tasks(assigned_user_id);
 *   CREATE INDEX idx_tasks_customer  ON tasks(customer_id);
 *
 *   -- Default sort  (covers OPEN-first + startAt + updatedAt)
 *   CREATE INDEX idx_tasks_sort ON tasks(status, start_at ASC NULLS LAST, updated_at DESC);
 *
 *   -- Overdue partial index (PostgreSQL)
 *   CREATE INDEX idx_tasks_overdue ON tasks(status, end_at)
 *       WHERE end_at IS NOT NULL;
 * </pre>
 */
public final class TaskSpecification {

    private TaskSpecification() {}

    /** Case-insensitive LIKE match on title and description. */
    public static Specification<TaskEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    public static Specification<TaskEntity> hasStatus(TaskStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<TaskEntity> hasPriority(TaskPriority priority) {
        if (priority == null) return null;
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<TaskEntity> assignedTo(UUID userId) {
        if (userId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("assignedUser").get("userId"), userId);
    }

    /**
     * Filters by customer. Tasks with no customer are excluded when this spec is active.
     * When {@code customerId} is null the spec is omitted entirely, so customerless tasks are included.
     */
    public static Specification<TaskEntity> forCustomer(UUID customerId) {
        if (customerId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("customer").get("customerId"), customerId);
    }

    /**
     * Overdue = not COMPLETED, not CANCELLED, endAt non-null and already past.
     * OVERDUE is a computed state — it is never stored as a status value in the database.
     */
    public static Specification<TaskEntity> isOverdue() {
        return (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), TaskStatus.COMPLETED),
                cb.notEqual(root.get("status"), TaskStatus.CANCELLED),
                root.get("endAt").isNotNull(),
                cb.lessThan(root.get("endAt"), OffsetDateTime.now())
        );
    }

    /**
     * Injects the standard task sort order into the {@link jakarta.persistence.criteria.CriteriaQuery}:
     * <ol>
     *   <li>OPEN tasks first</li>
     *   <li>Scheduled tasks (startAt non-null) before unscheduled</li>
     *   <li>Earliest startAt ASC</li>
     *   <li>Most recently updated DESC (tie-breaker)</li>
     * </ol>
     *
     * <p>Automatically skipped on count queries (result type {@code Long}) because
     * ORDER BY is invalid inside a {@code COUNT(*)} subquery.
     *
     * <p>Always pass {@code Sort.unsorted()} (or {@code PageRequest.of(page, size)}) in the
     * {@code Pageable} when this spec is active to prevent Spring Data from appending a
     * second ORDER BY clause after the one injected here.
     */
    public static Specification<TaskEntity> defaultSort() {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType())) return cb.conjunction();
            query.orderBy(
                    cb.asc(cb.<Integer>selectCase()
                            .when(cb.equal(root.get("status"), TaskStatus.OPEN), 0)
                            .otherwise(1)),
                    cb.asc(cb.<Integer>selectCase()
                            .when(root.get("startAt").isNull(), 1)
                            .otherwise(0)),
                    cb.asc(root.get("startAt")),
                    cb.desc(root.get("updatedAt"))
            );
            return cb.conjunction();
        };
    }
}
