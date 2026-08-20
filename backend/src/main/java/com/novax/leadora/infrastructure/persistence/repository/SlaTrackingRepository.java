package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SlaTrackingRepository extends JpaRepository<SlaTrackingEntity, UUID> {
        boolean existsByRuleId(UUID ruleId);

        List<SlaTrackingEntity> findByEntityType(String entityType);

        List<SlaTrackingEntity> findByEntityTypeAndEntityId(String entityType, UUID entityId);

        List<SlaTrackingEntity> findByStatusAndDeadlineAtBefore(SlaStatus status, OffsetDateTime before);

        List<SlaTrackingEntity> findByStatusIn(List<SlaStatus> statuses);

        List<SlaTrackingEntity> findByStatusInAndEntityType(List<SlaStatus> statuses, String entityType);

        List<SlaTrackingEntity> findByStatusAndWarningAtBeforeAndWarningNotifiedFalse(SlaStatus status,
                        OffsetDateTime before);

        /**
         * UC-17.4: BREACHED records past escalationAt that haven't been escalated yet
         */
        List<SlaTrackingEntity> findByStatusAndEscalationAtBeforeAndEscalationNotifiedFalse(SlaStatus status,
                        OffsetDateTime before);

        /**
         * UC-17.6 / UC-23.3: the tuples the compliance report classifies, with optional
         * entityType /
         * activityType filters.
         *
         * <p>
         * Five scalars per record rather than the managed entity: the report only ever
         * needs to
         * compare {@code resolvedAt} against {@code deadlineAt} and bucket by activity,
         * and selecting
         * entities made every row a persistence-context citizen for no benefit.
         *
         * <p>
         * Returns
         * {@code [activityType, status, startedAt, warningAt, deadlineAt, resolvedAt]}.
         * The range is half-open: {@code [start, end)} — see ReportRange.
         */
        @Query("""
                        SELECT s.activityType, s.status, s.startedAt, s.warningAt, s.deadlineAt, s.resolvedAt
                        FROM SlaTrackingEntity s
                        WHERE s.startedAt >= :start AND s.startedAt < :end
                          AND (:entityType IS NULL OR s.entityType = :entityType)
                          AND (:activityType IS NULL OR s.activityType = :activityType)
                        ORDER BY s.startedAt ASC
                        """)
        List<Object[]> findComplianceRows(
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end,
                        @Param("entityType") String entityType,
                        @Param("activityType") String activityType);

        /**
         * The dashboard's SLA figures, narrowed to what one user is allowed to see (BR-02).
         *
         * <p>The dashboard used to read {@code findAll()} here, so a Sales Staff saw the SLA
         * compliance of the whole company on a screen that carefully hid every colleague's lead,
         * deal and task directly above it. An aggregate is still information.
         *
         * <p>An SLA row names its subject polymorphically ({@code entity_type} + {@code entity_id})
         * and carries no assignee of its own, so ownership has to be resolved by joining whichever
         * subject it points at - the same four-way join {@code ChatAggregateRepository} uses for
         * the chat snapshot, and it must stay in step with it. A null {@code :scope} means "every
         * record", which is what a Manager or Admin gets.
         *
         * <p>Returns {@code [status, startedAt, deadlineAt, resolvedAt]} - only the columns the
         * compliance and response-time maths actually reads.
         */
        @Query(value = """
                        SELECT st.status, st.started_at, st.deadline_at, st.resolved_at
                          FROM sla_tracking st
                          LEFT JOIN leads      sl  ON st.entity_type = 'LEAD'      AND sl.lead_id      = st.entity_id
                          LEFT JOIN tasks      stk ON st.entity_type = 'TASK'      AND stk.task_id     = st.entity_id
                          LEFT JOIN quotations sq  ON st.entity_type = 'QUOTATION' AND sq.quotation_id = st.entity_id
                          LEFT JOIN bookings   sb  ON st.entity_type = 'BOOKING'   AND sb.booking_id   = st.entity_id
                         WHERE (CAST(:scope AS uuid) IS NULL
                                OR (st.entity_type = 'LEAD' AND sl.assigned_user_id = CAST(:scope AS uuid))
                                OR (st.entity_type = 'TASK' AND stk.assigned_user_id = CAST(:scope AS uuid))
                                OR (st.entity_type = 'QUOTATION' AND sq.created_by = CAST(:scope AS uuid))
                                OR (st.entity_type = 'BOOKING' AND sb.assigned_user_id = CAST(:scope AS uuid)))
                        """, nativeQuery = true)
        List<Object[]> findDashboardRows(@Param("scope") String scopeUserId);
}
