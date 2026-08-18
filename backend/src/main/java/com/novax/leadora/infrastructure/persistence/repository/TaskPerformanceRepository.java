package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UC-23.2 aggregates, as one statement.
 *
 * <p>Replaces four separate queries whose results the caller then had to hold together by hand. The
 * per-staff breakdown carries the owner as a grouping column rather than living in its own query,
 * so the staff rows <em>are</em> the headline rows before they are summed — the table adding up to
 * the totals is structural rather than something to remember.
 *
 * <p>Ranges are half-open: {@code [start, end)}. Two time axes, and the difference is the point of
 * this report: <b>raised</b> counts tasks by {@code created_at} (how much work came in), while
 * <b>resolved</b> counts them by {@code completed_at} (how much was finished, and whether it was
 * finished in time). A queue metric belongs to neither — it is measured against the clock, now.
 *
 * <p>Prose stays in Javadoc and never inside the statement: Spring Data parses the query text
 * before Postgres sees it, and an apostrophe in a {@code --} comment reads to that parser as the
 * start of a string literal, which fails repository creation at startup.
 */
public interface TaskPerformanceRepository extends Repository<TaskEntity, UUID> {

    /**
     * Every UC-23.2 aggregate, as {@code [metric, bucket, ownerId, ownerName, count, value]}.
     *
     * <p>{@code metric} names the population and the axis it sits on; {@code bucket} subdivides it.
     * A null {@code ownerId} is the unassigned group, deliberately kept so the staff table
     * reconciles with the totals.
     *
     * <p>The {@code RESOLVED} branch is where the report's original defect is fixed. It classifies
     * finished tasks as ON_TIME or LATE by comparing the completion time with the deadline, so a
     * task finished three days late is counted as late instead of vanishing from the overdue figure
     * the moment it left the OPEN status. {@code UNDATED} is the honest third bucket: tasks
     * completed before {@code completed_at} existed, which can be counted but not judged.
     *
     * @param assignedUserId scope filter — null for the whole team, otherwise one staff member
     * @param now            the cut-off the open queue is measured against (BR-17: overdue is
     *                       derived from the clock, never stored)
     */
    @Query(value = """
            SELECT 'RAISED' AS metric, t.status::text AS bucket,
                   u.user_id AS owner_id, u.full_name AS owner_name,
                   count(*) AS cnt, 0::numeric AS value
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY t.status, u.user_id, u.full_name
            UNION ALL
            SELECT 'RAISED_PRIORITY', t.priority::text, u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY t.priority, u.user_id, u.full_name
            UNION ALL
            SELECT 'RAISED_ACTIVITY', COALESCE(t.activity_type::text, 'UNSPECIFIED'),
                   u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY t.activity_type, u.user_id, u.full_name
            UNION ALL
            SELECT 'RAISED_LINKAGE',
                   CASE WHEN t.lead_id IS NULL AND t.customer_id IS NULL AND t.deal_id IS NULL
                        THEN 'ORPHAN' ELSE 'LINKED' END,
                   u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'RAISED_COMPLETED_UNDATED', 'UNDATED', u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND t.status = 'COMPLETED' AND t.completed_at IS NULL
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'RESOLVED',
                   CASE WHEN t.end_at IS NULL THEN 'UNDATED'
                        WHEN t.completed_at <= t.end_at THEN 'ON_TIME'
                        ELSE 'LATE' END,
                   u.user_id, u.full_name, count(*),
                   COALESCE(avg(EXTRACT(EPOCH FROM (t.completed_at - t.created_at)) / 3600.0), 0)
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.completed_at >= :start AND t.completed_at < :end
               AND t.status = 'COMPLETED'
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'OPEN_OVERDUE',
                   CASE WHEN :now - t.end_at < interval '4 days' THEN 'D1_3'
                        WHEN :now - t.end_at < interval '8 days' THEN 'D4_7'
                        ELSE 'D8_PLUS' END,
                   u.user_id, u.full_name, count(*),
                   COALESCE(avg(EXTRACT(EPOCH FROM (:now - t.end_at)) / 86400.0), 0)
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND t.end_at IS NOT NULL AND t.end_at < :now
               AND t.status NOT IN ('COMPLETED', 'CANCELLED')
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'OPEN_OVERDUE_PRIORITY', t.priority::text, u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.created_at >= :start AND t.created_at < :end
               AND t.end_at IS NOT NULL AND t.end_at < :now
               AND t.status NOT IN ('COMPLETED', 'CANCELLED')
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
             GROUP BY t.priority, u.user_id, u.full_name
            """, nativeQuery = true)
    List<Object[]> taskPerformanceAggregates(
            @Param("assignedUserId") String assignedUserId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("now") OffsetDateTime now);

    /**
     * One row per SLA tracked against a task, as {@code [ownerId, ownerName, status, warningAt,
     * deadlineAt, resolvedAt]}, so the caller can classify them with the same code UC-23.3 uses.
     *
     * <p>Rows rather than counts, for the same reason as the rep scorecard: classifying in SQL would
     * mean writing the "was this on time" rules a second time, in a second language, and
     * {@code SlaOutcomeClassifier} exists so those rules live in exactly one place.
     */
    @Query(value = """
            SELECT t.assigned_user_id AS owner_id, u.full_name AS owner_name,
                   s.status, s.warning_at, s.deadline_at, s.resolved_at
              FROM sla_tracking s
                   JOIN tasks t ON t.task_id = s.entity_id
                   LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE s.entity_type = 'TASK'
               AND s.started_at >= :start AND s.started_at < :end
               AND (CAST(:assignedUserId AS uuid) IS NULL OR t.assigned_user_id = CAST(:assignedUserId AS uuid))
            """, nativeQuery = true)
    List<Object[]> taskSlaRows(
            @Param("assignedUserId") String assignedUserId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
